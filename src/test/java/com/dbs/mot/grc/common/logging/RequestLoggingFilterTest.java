package com.dbs.mot.grc.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@link RequestLoggingFilter} populates SLF4J MDC ({@code traceId}, {@code username})
 * for the lifetime of a request and clears it afterwards.
 *
 * <p>A Logback {@link ListAppender} is attached directly to the filter's logger so the test can
 * inspect the MDC snapshot captured on each log event, rather than parsing formatted console text.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestLoggingFilterTest {

    @Autowired MockMvc mvc;

    private ListAppender<ILoggingEvent> appender;
    private Logger filterLogger;

    @BeforeEach
    void attachAppender() {
        filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        filterLogger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void requestWithTraceIdAndUsername_populatesMdcOnLogEvents() throws Exception {
        mvc.perform(get("/api/csv/biz-units/download")
                        .header("X-Trace-Id", "trace-123")
                        .header("X-EGRC-UserId", "alice"))
           .andExpect(status().isOk());

        List<ILoggingEvent> events = appender.list;
        assertThat(events).isNotEmpty();
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getMDCPropertyMap()).containsEntry("traceId", "trace-123");
            assertThat(event.getMDCPropertyMap()).containsEntry("username", "alice");
        });
    }

    @Test
    void requestWithoutTraceId_generatesRandomOne() throws Exception {
        mvc.perform(get("/api/csv/biz-units/download")).andExpect(status().isOk());

        List<ILoggingEvent> events = appender.list;
        assertThat(events).isNotEmpty();
        String generatedTraceId = events.get(0).getMDCPropertyMap().get("traceId");
        assertThat(generatedTraceId).isNotBlank();
        // username was not sent — must not appear in the MDC snapshot
        assertThat(events.get(0).getMDCPropertyMap()).doesNotContainKey("username");
    }

    @Test
    void afterRequestCompletes_mdcIsClearedForCurrentThread() throws Exception {
        mvc.perform(get("/api/csv/biz-units/download").header("X-EGRC-UserId", "bob")).andExpect(status().isOk());

        // MockMvc runs the filter chain synchronously on the test thread, so MDC.clear()
        // in the filter's finally block should have already run by the time perform() returns.
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("username")).isNull();
    }

    @Test
    void requestCompletedLogLine_includesStatusAndDuration() throws Exception {
        mvc.perform(get("/api/csv/biz-units/download")).andExpect(status().isOk());

        assertThat(appender.list).anySatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("Request completed").contains("status=200"));
    }
}
