package com.minjikim.codecobainbackend.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.UUID;

// ResponseEntityExceptionHandler를 상속해 400·404·413 같은 Spring MVC 예외가 아래 Exception 핸들러의 500으로 바뀌지 않게 한다
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WaferException.class)
    public ProblemDetail handleWaferException(WaferException ex) {
        ErrorCode code = ex.getErrorCode();
        String traceId = resolveTraceId();
        if (code.getStatus().is5xxServerError()) {
            log.error("요청 실패: code={}, traceId={}, message={}", code, traceId, ex.getMessage(), ex);
        } else {
            log.warn("요청 거부: code={}, traceId={}, message={}", code, traceId, ex.getMessage());
        }
        return toProblemDetail(code, ex.getMessage(), traceId);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        String traceId = resolveTraceId();
        log.error("처리되지 않은 예외: traceId={}", traceId, ex);
        return toProblemDetail(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage(), traceId);
    }

    private ProblemDetail toProblemDetail(ErrorCode code, String detail, String traceId) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.getStatus(), detail);
        problem.setTitle(code.name());
        problem.setProperty("code", code.name());
        problem.setProperty("traceId", traceId);
        return problem;
    }

    // OTel agent가 없는 dev 프로파일에서는 MDC가 비어 있으므로, 응답과 로그를 잇는 ID를 여기서 만든다
    private String resolveTraceId() {
        String traceId = MDC.get("trace_id");
        return traceId != null && !traceId.isBlank() ? traceId : UUID.randomUUID().toString().replace("-", "");
    }
}
