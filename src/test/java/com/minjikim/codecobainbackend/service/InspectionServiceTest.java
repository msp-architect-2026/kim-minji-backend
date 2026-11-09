package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.AiPredictionResponse;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.util.ImageFileValidator;
import com.minjikim.codecobainbackend.util.MinioUploader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    private final MockMultipartFile file = new MockMultipartFile("file", "w.png", "image/png", new byte[]{1});

    @Mock private ImageFileValidator validator;
    @Mock private MinioUploader uploader;
    @Mock private AiAnalysisService ai;
    @Mock private WaferImageService images;
    @InjectMocks private InspectionService service;

    @Test
    void validatesUploadsAnalyzesThenSaves() {
        AiPredictionResponse aiResult = new AiPredictionResponse(null, "Donut", 0.9);
        when(uploader.upload(file, "wafer-images")).thenReturn("key");
        when(ai.analyze("key")).thenReturn(aiResult);
        when(images.save("w.png", "key", aiResult)).thenReturn(7L);

        AiPredictionResponse result = service.inspect(file);

        InOrder order = inOrder(validator, uploader, ai, images);
        order.verify(validator).validate(file);
        order.verify(uploader).upload(file, "wafer-images");
        order.verify(ai).analyze("key");
        order.verify(images).save("w.png", "key", aiResult);
        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getPrediction()).isEqualTo("Donut");
    }

    @Test
    void doesNotSaveWhenAnalysisFails() {
        when(uploader.upload(file, "wafer-images")).thenReturn("key");
        when(ai.analyze("key")).thenThrow(new WaferException(ErrorCode.AI_TIMEOUT));

        assertThatThrownBy(() -> service.inspect(file)).isInstanceOf(WaferException.class);

        verify(images, never()).save(any(), any(), any());
    }
}
