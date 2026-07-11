package com.dbs.mot.grc.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Puts a request-scoped {@code traceId} and {@code username} into SLF4J MDC so every
 * log line for a request can be correlated, and logs one line on entry and one on
 * completion (with duration).
 *
 * <p>{@code X-EGRC-UserId} is read on a best-effort basis — it is not required for every
 * request (e.g. GET/download requests do not send it), unlike the CSV upload endpoint
 * which enforces it explicitly in {@link com.dbs.mot.grc.common.controller.CsvController}.
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    static final String TRACE_ID_HEADER = "X-Trace-Id";
    static final String USERNAME_HEADER = "X-EGRC-UserId";
    static final String MDC_TRACE_ID = "traceId";
    static final String MDC_USERNAME = "username";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        String username = request.getHeader(USERNAME_HEADER);

        MDC.put(MDC_TRACE_ID, traceId);
        if (username != null && !username.isBlank()) {
            MDC.put(MDC_USERNAME, username);
        }

        long start = System.currentTimeMillis();
        log.info("Request started: {} {}", request.getMethod(), request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            log.info("Request completed: {} {} -> status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
            MDC.clear();
        }
    }
}
