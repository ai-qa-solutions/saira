package io.saira.service.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import io.saira.dto.TraceDetailResponse;
import io.saira.dto.TraceListItemResponse;
import io.saira.entity.AgentSpan;
import io.saira.entity.AgentTrace;
import io.saira.repository.AgentSpanRepository;
import io.saira.repository.AgentTraceRepository;

/** Unit-тесты для TraceService. */
@ExtendWith(MockitoExtension.class)
class TraceServiceTest {

    @Mock
    private AgentTraceRepository traceRepository;

    @Mock
    private AgentSpanRepository spanRepository;

    @InjectMocks
    private TraceService traceService;

    @Test
    @DisplayName("should_ReturnPageOfTraces_When_DataExists")
    void should_ReturnPageOfTraces_When_DataExists() {
        // given
        final Instant now = Instant.parse("2026-02-24T08:49:03Z");
        final AgentTrace trace1 = AgentTrace.builder()
                .id(1L)
                .traceId("trace-001")
                .serviceName("agent-service")
                .fpId("fp-100")
                .fpModuleId("module-a")
                .startedAt(now)
                .endedAt(now.plusSeconds(5))
                .spanCount(3)
                .status("RECEIVED")
                .createdAt(now)
                .build();
        final AgentTrace trace2 = AgentTrace.builder()
                .id(2L)
                .traceId("trace-002")
                .serviceName("agent-service")
                .fpId("fp-200")
                .fpModuleId("module-b")
                .startedAt(now.plusSeconds(10))
                .endedAt(now.plusSeconds(15))
                .spanCount(5)
                .status("PROCESSED")
                .createdAt(now.plusSeconds(10))
                .build();
        final Page<AgentTrace> tracePage = new PageImpl<>(List.of(trace1, trace2));
        when(traceRepository.findAllByOrderByStartedAtDesc(any(Pageable.class))).thenReturn(tracePage);

        // when
        final Page<TraceListItemResponse> result = traceService.getTraces(Pageable.ofSize(10));

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).traceId()).isEqualTo("trace-001");
        assertThat(result.getContent().get(1).traceId()).isEqualTo("trace-002");
    }

    @Test
    @DisplayName("should_ReturnTraceDetail_When_TraceExists")
    void should_ReturnTraceDetail_When_TraceExists() {
        // given
        final Instant now = Instant.parse("2026-02-24T08:49:03Z");
        final AgentTrace trace = AgentTrace.builder()
                .id(1L)
                .traceId("abc")
                .serviceName("agent-service")
                .fpId("fp-100")
                .fpModuleId("module-a")
                .startedAt(now)
                .endedAt(now.plusSeconds(10))
                .spanCount(3)
                .status("RECEIVED")
                .createdAt(now)
                .build();
        final List<AgentSpan> spans = List.of(
                AgentSpan.builder()
                        .id(1L)
                        .traceId("abc")
                        .spanId("span-001")
                        .parentSpanId(null)
                        .operationName("POST /completions")
                        .spanKind("CLIENT")
                        .startedAt(now)
                        .durationMicros(1000L)
                        .statusCode("OK")
                        .httpUrl("https://api.openai.com/v1/completions")
                        .httpMethod("POST")
                        .httpStatus("200")
                        .clientName("openai")
                        .requestBody("{\"prompt\":\"hello\"}")
                        .responseBody("{\"text\":\"world\"}")
                        .outcome("SUCCESS")
                        .build(),
                AgentSpan.builder()
                        .id(2L)
                        .traceId("abc")
                        .spanId("span-002")
                        .parentSpanId("span-001")
                        .operationName("POST /embeddings")
                        .spanKind("CLIENT")
                        .startedAt(now.plusSeconds(1))
                        .durationMicros(500L)
                        .statusCode("OK")
                        .httpUrl("https://api.openai.com/v1/embeddings")
                        .httpMethod("POST")
                        .httpStatus("200")
                        .clientName("openai")
                        .requestBody("{\"input\":\"test\"}")
                        .responseBody("{\"data\":[]}")
                        .outcome("SUCCESS")
                        .build(),
                AgentSpan.builder()
                        .id(3L)
                        .traceId("abc")
                        .spanId("span-003")
                        .parentSpanId("span-001")
                        .operationName("POST /mcp")
                        .spanKind("CLIENT")
                        .startedAt(now.plusSeconds(2))
                        .durationMicros(200L)
                        .statusCode("OK")
                        .httpUrl("https://mcp.example.com/tools")
                        .httpMethod("POST")
                        .httpStatus("200")
                        .clientName("mcp-server")
                        .requestBody("{\"tool\":\"search\"}")
                        .responseBody("{\"result\":\"found\"}")
                        .outcome("SUCCESS")
                        .build());
        when(traceRepository.findByTraceId("abc")).thenReturn(Optional.of(trace));
        when(spanRepository.findByTraceIdOrderByStartedAtAsc("abc")).thenReturn(spans);

        // when
        final TraceDetailResponse result = traceService.getTraceDetail("abc");

        // then
        assertThat(result.traceId()).isEqualTo("abc");
        assertThat(result.serviceName()).isEqualTo("agent-service");
        assertThat(result.spans()).hasSize(3);
        assertThat(result.spans().get(0).spanId()).isEqualTo("span-001");
        assertThat(result.spans().get(1).spanId()).isEqualTo("span-002");
        assertThat(result.spans().get(2).spanId()).isEqualTo("span-003");
    }

    @Test
    @DisplayName("should_Throw404_When_TraceNotFound")
    void should_Throw404_When_TraceNotFound() {
        // given
        when(traceRepository.findByTraceId("unknown")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> traceService.getTraceDetail("unknown"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Trace not found: unknown");
    }
}
