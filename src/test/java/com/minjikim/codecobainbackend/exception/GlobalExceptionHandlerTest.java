package com.minjikim.codecobainbackend.exception;

import com.minjikim.codecobainbackend.controller.AiPredictionController;
import com.minjikim.codecobainbackend.service.InspectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMultipartFile file = new MockMultipartFile("file", "w.png", "image/png", new byte[]{1});

    private final InspectionService inspectionService = mock(InspectionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AiPredictionController(inspectionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void waferExceptionReturnsMappedStatusCodeAndTraceId() throws Exception {
        when(inspectionService.inspect(any())).thenThrow(new WaferException(ErrorCode.AI_CIRCUIT_OPEN));

        mockMvc.perform(multipart("/ai/predict").file(file))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_CIRCUIT_OPEN"))
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())));
    }

    @Test
    void unexpectedExceptionDoesNotLeakDetails() throws Exception {
        when(inspectionService.inspect(any())).thenThrow(new IllegalStateException("db password=secret"));

        mockMvc.perform(multipart("/ai/predict").file(file))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(ErrorCode.INTERNAL_ERROR.getMessage()));
    }

    @Test
    void missingFilePartStaysBadRequest() throws Exception {
        mockMvc.perform(multipart("/ai/predict"))
                .andExpect(status().isBadRequest());
    }
}
