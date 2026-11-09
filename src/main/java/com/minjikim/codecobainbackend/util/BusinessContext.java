package com.minjikim.codecobainbackend.util;

import io.opentelemetry.api.trace.Span;
import org.slf4j.MDC;

// 로그와 span에 업무 키를 붙여 "이 Lot·검사가 왜 실패했나"에서 바로 trace로 갈 수 있게 한다
public final class BusinessContext {

    public static final String LOT_KEY = "lot_id";
    public static final String INSPECTION_KEY = "inspection_id";

    private BusinessContext() {
    }

    public static void bind(Long lotId, Long inspectionId) {
        if (lotId != null) {
            MDC.put(LOT_KEY, lotId.toString());
            Span.current().setAttribute("mes.lot_id", lotId);
        }
        if (inspectionId != null) {
            MDC.put(INSPECTION_KEY, inspectionId.toString());
            Span.current().setAttribute("mes.inspection_id", inspectionId);
        }
    }

    public static void clear() {
        MDC.remove(LOT_KEY);
        MDC.remove(INSPECTION_KEY);
    }
}
