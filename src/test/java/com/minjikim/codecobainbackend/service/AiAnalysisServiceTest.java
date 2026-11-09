package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiAnalysisServiceTest {

    private static final String AI_URL = "http://ai/predict";

    private MockRestServiceServer server;
    private CircuitBreakerRegistry registry;
    private AiAnalysisService service;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        registry = CircuitBreakerRegistry.ofDefaults();
        service = new AiAnalysisService(restTemplate, registry);
        ReflectionTestUtils.setField(service, "aiUrl", AI_URL);
        ReflectionTestUtils.setField(service, "bucket", "bucket");
    }

    @Test
    void returnsPredictionOnSuccess() {
        respond(withSuccess("{\"prediction\":\"Edge-Loc\",\"confidence\":0.98}", MediaType.APPLICATION_JSON));

        assertThat(service.analyze("key").getPrediction()).isEqualTo("Edge-Loc");
    }

    @Test
    void readTimeoutMapsToAiTimeout() {
        respond(request -> { throw new SocketTimeoutException("Read timed out"); });

        assertFailsWith(ErrorCode.AI_TIMEOUT);
    }

    @Test
    void connectionFailureMapsToAiUnavailable() {
        respond(request -> { throw new ConnectException("Connection refused"); });

        assertFailsWith(ErrorCode.AI_UNAVAILABLE);
    }

    @Test
    void errorStatusMapsToAiServerError() {
        respond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertFailsWith(ErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void rejectedInputMapsToInvalidFile() {
        respond(withStatus(HttpStatus.BAD_REQUEST));

        assertFailsWith(ErrorCode.INVALID_FILE);
    }

    @Test
    void unknownLabelIsRejected() {
        respond(withSuccess("{\"prediction\":\"Unknown\",\"confidence\":0.5}", MediaType.APPLICATION_JSON));

        assertFailsWith(ErrorCode.AI_INVALID_RESPONSE);
    }

    @Test
    void outOfRangeConfidenceIsRejected() {
        respond(withSuccess("{\"prediction\":\"none\",\"confidence\":1.5}", MediaType.APPLICATION_JSON));

        assertFailsWith(ErrorCode.AI_INVALID_RESPONSE);
    }

    @Test
    void openCircuitDoesNotCallAiServer() {
        registry.circuitBreaker("aiServing").transitionToOpenState();

        assertFailsWith(ErrorCode.AI_CIRCUIT_OPEN);
        server.verify();
    }

    private void respond(ResponseCreator creator) {
        server.expect(requestTo(AI_URL)).andRespond(creator);
    }

    private void assertFailsWith(ErrorCode code) {
        assertThatThrownBy(() -> service.analyze("key"))
                .isInstanceOfSatisfying(WaferException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
