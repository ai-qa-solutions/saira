package io.saira.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import io.saira.config.SecurityConfig;
import io.saira.dto.SpanResponse;
import io.saira.dto.TraceDetailResponse;
import io.saira.dto.TraceListItemResponse;
import io.saira.service.trace.TraceService;

/** Тесты REST контроллера для работы с трейсами агентов. */
@WebMvcTest(TraceController.class)
@Import(SecurityConfig.class)
class TraceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TraceService traceService;

    @Test
    @DisplayName("should_Return200WithPage_When_GetTraces")
    void should_Return200WithPage_When_GetTraces() throws Exception {
        // given
        final Instant now = Instant.parse("2026-02-24T08:49:03Z");
        final TraceListItemResponse item = new TraceListItemResponse(
                "trace-001", "agent-service", "fp-100", "module-a", now, now.plusSeconds(5), 3, "RECEIVED", now);
        final Page<TraceListItemResponse> page = new PageImpl<>(List.of(item));
        when(traceService.getTraces(any(Pageable.class))).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/traces")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].traceId").value("trace-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("should_Return200WithDetail_When_TraceExists")
    void should_Return200WithDetail_When_TraceExists() throws Exception {
        // given
        final Instant now = Instant.parse("2026-02-24T08:49:03Z");
        final SpanResponse span1 = new SpanResponse(
                "span-001",
                null,
                "POST /completions",
                "CLIENT",
                now,
                1000L,
                "OK",
                "https://api.openai.com/v1/completions",
                "POST",
                "200",
                "openai",
                "{\"prompt\":\"hello\"}",
                "{\"text\":\"world\"}",
                "SUCCESS");
        final SpanResponse span2 = new SpanResponse(
                "span-002",
                "span-001",
                "POST /embeddings",
                "CLIENT",
                now.plusSeconds(1),
                500L,
                "OK",
                "https://api.openai.com/v1/embeddings",
                "POST",
                "200",
                "openai",
                "{\"input\":\"test\"}",
                "{\"data\":[]}",
                "SUCCESS");
        final TraceDetailResponse detail = new TraceDetailResponse(
                "trace-123",
                "agent-service",
                "fp-100",
                "module-a",
                now,
                now.plusSeconds(10),
                2,
                "RECEIVED",
                now,
                List.of(span1, span2));
        when(traceService.getTraceDetail("trace-123")).thenReturn(detail);

        // when & then
        mockMvc.perform(get("/api/v1/traces/trace-123").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value("trace-123"))
                .andExpect(jsonPath("$.spans.length()").value(2));
    }

    @Test
    @DisplayName("should_Return404_When_TraceNotFound")
    void should_Return404_When_TraceNotFound() throws Exception {
        // given
        when(traceService.getTraceDetail("unknown"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace not found: unknown"));

        // when & then
        mockMvc.perform(get("/api/v1/traces/unknown").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_ReturnEmptyPage_When_NoTraces")
    void should_ReturnEmptyPage_When_NoTraces() throws Exception {
        // given
        when(traceService.getTraces(any(Pageable.class))).thenReturn(Page.empty());

        // when & then
        mockMvc.perform(get("/api/v1/traces").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
