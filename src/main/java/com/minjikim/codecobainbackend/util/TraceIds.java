package com.minjikim.codecobainbackend.util;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.MDC;

import java.util.UUID;

public final class TraceIds {

    public static final String MDC_KEY = "trace_id";

    private TraceIds() {
    }

    // OTel agent는 trace_id를 로그 이벤트에만 넣고 MDC.get()으로는 보이지 않으므로 Span에서 먼저 읽는다
    public static String current() {
        SpanContext context = Span.current().getSpanContext();
        if (context.isValid()) {
            return context.getTraceId();
        }
        String traceId = MDC.get(MDC_KEY);
        return traceId != null && !traceId.isBlank() ? traceId : generate();
    }

    public static boolean hasActiveSpan() {
        return Span.current().getSpanContext().isValid();
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
