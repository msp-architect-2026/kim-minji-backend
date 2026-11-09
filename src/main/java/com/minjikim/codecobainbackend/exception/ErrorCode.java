package com.minjikim.codecobainbackend.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_FILE(HttpStatus.BAD_REQUEST, "유효하지 않은 파일입니다."),
    RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 기록을 찾을 수 없습니다."),
    STORAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장에 실패했습니다."),
    AI_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AI 서버가 오류를 반환했습니다."),
    AI_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "AI 서버 응답이 올바르지 않습니다."),
    AI_CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "AI 서비스가 일시 중단되었습니다."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서버에 연결할 수 없습니다."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 서버 응답 시간이 초과되었습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
