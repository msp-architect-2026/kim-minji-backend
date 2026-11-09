package com.minjikim.codecobainbackend.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_FILE(HttpStatus.BAD_REQUEST, "유효하지 않은 파일입니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 기록을 찾을 수 없습니다."),
    LOT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 Lot을 찾을 수 없습니다."),
    WAFER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 웨이퍼를 찾을 수 없습니다."),
    INSPECTION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 검사를 찾을 수 없습니다."),
    STEP_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 공정을 찾을 수 없습니다."),
    EQUIPMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 설비를 찾을 수 없습니다."),
    ROUTE_VIOLATION(HttpStatus.CONFLICT, "공정 순서가 올바르지 않습니다."),
    LOT_ALREADY_TRACKED_IN(HttpStatus.CONFLICT, "이미 다른 설비에서 진행 중인 Lot입니다."),
    LOT_NOT_TRACKED_IN(HttpStatus.CONFLICT, "해당 공정에서 진행 중인 Lot이 아닙니다."),
    LOT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 Lot 번호입니다."),
    LOT_NOT_ACTIVE(HttpStatus.CONFLICT, "진행 중인 Lot이 아닙니다."),
    INSPECTION_IN_PROGRESS(HttpStatus.CONFLICT, "해당 웨이퍼는 검사 중입니다."),
    INSPECTION_NOT_RETRYABLE(HttpStatus.CONFLICT, "재시도할 수 없는 검사입니다."),
    INSPECTION_NOT_REVIEWABLE(HttpStatus.CONFLICT, "리뷰 대기 중인 검사가 아닙니다."),
    INSPECTION_NEEDS_REVIEW(HttpStatus.CONFLICT, "리뷰가 끝나야 다시 검사할 수 있습니다."),
    LOT_NOT_ON_HOLD(HttpStatus.CONFLICT, "Hold 상태인 Lot이 아닙니다."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "다른 요청이 먼저 처리되었습니다. 다시 시도해 주세요."),
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
