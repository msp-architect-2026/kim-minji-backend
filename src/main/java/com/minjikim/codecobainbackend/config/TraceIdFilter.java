package com.minjikim.codecobainbackend.config;

import com.minjikim.codecobainbackend.util.TraceIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// OTel agent가 없을 때도 한 요청의 로그·응답·검사 이력이 같은 trace_id를 쓰게 한다
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (TraceIds.hasActiveSpan()) {
            chain.doFilter(request, response);
            return;
        }
        MDC.put(TraceIds.MDC_KEY, TraceIds.generate());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
        }
    }
}
