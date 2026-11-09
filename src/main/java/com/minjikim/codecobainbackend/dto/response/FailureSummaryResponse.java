package com.minjikim.codecobainbackend.dto.response;

import java.util.List;

public record FailureSummaryResponse(int hours, long totalFailures, long unresolved, List<Row> rows) {
    public record Row(String stage, String errorCode, long total, long unresolved) {
    }
}
