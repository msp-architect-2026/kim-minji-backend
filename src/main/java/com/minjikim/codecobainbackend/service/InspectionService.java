package com.minjikim.codecobainbackend.service;

import com.minjikim.codecobainbackend.dto.response.AiPredictionResponse;
import com.minjikim.codecobainbackend.dto.response.InspectionEventResponse;
import com.minjikim.codecobainbackend.dto.response.InspectionResponse;
import com.minjikim.codecobainbackend.entity.InspectionEntity;
import com.minjikim.codecobainbackend.exception.ErrorCode;
import com.minjikim.codecobainbackend.exception.WaferException;
import com.minjikim.codecobainbackend.repository.InspectionEventRepository;
import com.minjikim.codecobainbackend.repository.InspectionRepository;
import com.minjikim.codecobainbackend.util.ImageFileValidator;
import com.minjikim.codecobainbackend.util.MinioUploader;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InspectionService {

    private static final Logger log = LoggerFactory.getLogger(InspectionService.class);
    private static final String IMAGE_DIR = "wafer-images";

    private final ImageFileValidator imageFileValidator;
    private final MinioUploader minioUploader;
    private final AiAnalysisService aiAnalysisService;
    private final InspectionLifecycle lifecycle;
    private final InspectionRepository inspections;
    private final InspectionEventRepository events;

    public AiPredictionResponse inspect(MultipartFile file) {
        log.info("검사 요청 수신: 파일명 = {}", file.getOriginalFilename());
        imageFileValidator.validate(file);

        Long id = lifecycle.beginWithoutWafer(file.getOriginalFilename());
        InspectionResponse result = uploadAndInfer(id, file);
        return new AiPredictionResponse(result.id(), result.prediction(), result.confidence());
    }

    public InspectionResponse inspectWafer(Long lotId, int slotNo, MultipartFile file, String idempotencyKey) {
        log.info("웨이퍼 검사 요청 수신: lotId={}, slotNo={}", lotId, slotNo);
        imageFileValidator.validate(file);

        Optional<InspectionResponse> replay = findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            return replay.get();
        }
        Long id;
        try {
            id = lifecycle.beginForWafer(lotId, slotNo, file.getOriginalFilename(), idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            // 같은 키의 요청이 동시에 들어와 다른 쪽이 먼저 저장한 경우
            return findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
        }
        return uploadAndInfer(id, file);
    }

    public InspectionResponse retry(Long id) {
        log.info("검사 재시도 요청: inspectionId={}", id);
        return infer(id, lifecycle.startRetry(id));
    }

    public List<InspectionEventResponse> findEvents(Long id) {
        if (!inspections.existsById(id)) {
            throw new WaferException(ErrorCode.INSPECTION_NOT_FOUND);
        }
        return events.findByInspectionIdOrderById(id).stream().map(InspectionEventResponse::from).toList();
    }

    private Optional<InspectionResponse> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return inspections.findByIdempotencyKey(idempotencyKey)
                .map(InspectionEntity::getId)
                .map(lifecycle::view);
    }

    private InspectionResponse uploadAndInfer(Long id, MultipartFile file) {
        String imageKey;
        try {
            imageKey = minioUploader.upload(file, IMAGE_DIR);
        } catch (RuntimeException e) {
            lifecycle.fail(id, errorCodeOf(e));
            throw e;
        }
        lifecycle.markUploaded(id, imageKey);
        lifecycle.startInference(id);
        return infer(id, imageKey);
    }

    private InspectionResponse infer(Long id, String imageKey) {
        AiPredictionResponse result;
        try {
            result = aiAnalysisService.analyze(imageKey);
        } catch (RuntimeException e) {
            lifecycle.fail(id, errorCodeOf(e));
            throw e;
        }
        return lifecycle.complete(id, result);
    }

    private ErrorCode errorCodeOf(RuntimeException e) {
        return e instanceof WaferException waferException ? waferException.getErrorCode() : ErrorCode.INTERNAL_ERROR;
    }
}
