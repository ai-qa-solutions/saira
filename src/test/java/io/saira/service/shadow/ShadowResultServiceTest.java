package io.saira.service.shadow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import io.saira.dto.ShadowResultResponse;
import io.saira.dto.ShadowTraceItemResponse;
import io.saira.dto.mapper.ShadowMapper;
import io.saira.entity.AgentSpan;
import io.saira.entity.AgentTrace;
import io.saira.entity.ShadowEvaluation;
import io.saira.entity.ShadowResult;
import io.saira.repository.AgentSpanRepository;
import io.saira.repository.AgentTraceRepository;
import io.saira.repository.ShadowEvaluationRepository;
import io.saira.repository.ShadowResultRepository;

/** Unit-тесты сервиса хранения и получения shadow-результатов. */
@ExtendWith(MockitoExtension.class)
class ShadowResultServiceTest {

    @Mock
    private ShadowResultRepository shadowResultRepository;

    @Mock
    private ShadowEvaluationRepository shadowEvaluationRepository;

    @Mock
    private AgentSpanRepository agentSpanRepository;

    @Mock
    private AgentTraceRepository agentTraceRepository;

    @Mock
    private ShadowMapper shadowMapper;

    @InjectMocks
    private ShadowResultService shadowResultService;

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("should save and return result with id")
        void should_SaveAndReturn_When_ValidResult() {
            // given
            final ShadowResult result = ShadowResult.builder()
                    .sourceSpanId("span-001")
                    .shadowConfigId(1L)
                    .modelId("gpt-4")
                    .status("SUCCESS")
                    .build();
            final ShadowResult saved = ShadowResult.builder()
                    .id(1L)
                    .sourceSpanId("span-001")
                    .shadowConfigId(1L)
                    .modelId("gpt-4")
                    .status("SUCCESS")
                    .build();
            when(shadowResultRepository.save(result)).thenReturn(saved);

            // when
            final ShadowResult actual = shadowResultService.save(result);

            // then
            assertThat(actual.getId()).isEqualTo(1L);
            verify(shadowResultRepository).save(result);
        }

        @Test
        @DisplayName("should throw when result is null")
        void should_ThrowException_When_ResultNull() {
            // when & then
            assertThatThrownBy(() -> shadowResultService.save(null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getResultsForSpan()")
    class GetResultsForSpan {

        @Test
        @DisplayName("should return results with evaluations for span")
        void should_ReturnResultsWithEvaluations_When_SpanHasResults() {
            // given
            final ShadowResult result = ShadowResult.builder()
                    .id(1L)
                    .sourceSpanId("span-001")
                    .modelId("gpt-4")
                    .status("SUCCESS")
                    .build();
            when(shadowResultRepository.findBySourceSpanId("span-001")).thenReturn(List.of(result));

            final ShadowResultResponse response = ShadowResultResponse.builder()
                    .id(1L)
                    .sourceSpanId("span-001")
                    .modelId("gpt-4")
                    .build();
            when(shadowMapper.toResultResponseList(List.of(result))).thenReturn(List.of(response));

            final ShadowEvaluation evaluation = ShadowEvaluation.builder()
                    .id(1L)
                    .shadowResultId(1L)
                    .semanticSimilarity(new BigDecimal("0.9200"))
                    .correctnessScore(new BigDecimal("1.0000"))
                    .build();
            when(shadowEvaluationRepository.findByShadowResultId(1L)).thenReturn(List.of(evaluation));

            // when
            final List<ShadowResultResponse> results = shadowResultService.getResultsForSpan("span-001");

            // then
            assertThat(results).hasSize(1);
            assertThat(results.getFirst().getSemanticSimilarity()).isEqualByComparingTo(new BigDecimal("0.9200"));
            assertThat(results.getFirst().getCorrectnessScore()).isEqualByComparingTo(new BigDecimal("1.0000"));
        }

        @Test
        @DisplayName("should return empty list when span has no results")
        void should_ReturnEmptyList_When_NoResults() {
            // given
            when(shadowResultRepository.findBySourceSpanId("span-999")).thenReturn(Collections.emptyList());

            // when
            final List<ShadowResultResponse> results = shadowResultService.getResultsForSpan("span-999");

            // then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should throw when spanId is blank")
        void should_ThrowException_When_SpanIdBlank() {
            // when & then
            assertThatThrownBy(() -> shadowResultService.getResultsForSpan(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getResultsForTrace()")
    class GetResultsForTrace {

        @Test
        @DisplayName("should return results for all spans in trace")
        void should_ReturnResults_When_TraceHasSpans() {
            // given
            final AgentSpan span1 = AgentSpan.builder()
                    .spanId("span-001")
                    .traceId("trace-001")
                    .operationName("POST /completions")
                    .startedAt(Instant.now())
                    .durationMicros(1000L)
                    .build();
            final AgentSpan span2 = AgentSpan.builder()
                    .spanId("span-002")
                    .traceId("trace-001")
                    .operationName("POST /completions")
                    .startedAt(Instant.now())
                    .durationMicros(2000L)
                    .build();
            when(agentSpanRepository.findByTraceIdOrderByStartedAtAsc("trace-001"))
                    .thenReturn(List.of(span1, span2));

            final ShadowResult result = ShadowResult.builder()
                    .id(1L)
                    .sourceSpanId("span-001")
                    .modelId("gpt-4")
                    .status("SUCCESS")
                    .build();
            when(shadowResultRepository.findBySourceSpanIdIn(List.of("span-001", "span-002")))
                    .thenReturn(List.of(result));

            final ShadowResultResponse response = ShadowResultResponse.builder()
                    .id(1L)
                    .sourceSpanId("span-001")
                    .build();
            when(shadowMapper.toResultResponseList(List.of(result))).thenReturn(List.of(response));
            when(shadowEvaluationRepository.findByShadowResultId(1L)).thenReturn(Collections.emptyList());

            // when
            final List<ShadowResultResponse> results = shadowResultService.getResultsForTrace("trace-001");

            // then
            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list when trace has no spans")
        void should_ReturnEmptyList_When_TraceHasNoSpans() {
            // given
            when(agentSpanRepository.findByTraceIdOrderByStartedAtAsc("trace-999"))
                    .thenReturn(Collections.emptyList());

            // when
            final List<ShadowResultResponse> results = shadowResultService.getResultsForTrace("trace-999");

            // then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when spans have no shadow results")
        void should_ReturnEmptyList_When_SpansHaveNoResults() {
            // given
            final AgentSpan span = AgentSpan.builder()
                    .spanId("span-001")
                    .traceId("trace-001")
                    .operationName("POST /completions")
                    .startedAt(Instant.now())
                    .durationMicros(1000L)
                    .build();
            when(agentSpanRepository.findByTraceIdOrderByStartedAtAsc("trace-001"))
                    .thenReturn(List.of(span));
            when(shadowResultRepository.findBySourceSpanIdIn(List.of("span-001")))
                    .thenReturn(Collections.emptyList());

            // when
            final List<ShadowResultResponse> results = shadowResultService.getResultsForTrace("trace-001");

            // then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should throw when traceId is blank")
        void should_ThrowException_When_TraceIdBlank() {
            // when & then
            assertThatThrownBy(() -> shadowResultService.getResultsForTrace(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getTracesWithShadowResults()")
    class GetTracesWithShadowResults {

        @Test
        @DisplayName("should return aggregated traces with shadow data")
        void should_ReturnAggregatedTraces() {
            // given
            final Pageable pageable = PageRequest.of(0, 10);
            final ShadowResult result = ShadowResult.builder()
                    .id(1L)
                    .sourceSpanId("span-001")
                    .modelId("gpt-4")
                    .executedAt(Instant.now())
                    .build();
            when(shadowResultRepository.findAll()).thenReturn(List.of(result));

            final AgentSpan span =
                    AgentSpan.builder().spanId("span-001").traceId("trace-001").build();
            when(agentSpanRepository.findBySpanIdIn(List.of("span-001"))).thenReturn(List.of(span));

            final AgentTrace trace = AgentTrace.builder()
                    .traceId("trace-001")
                    .serviceName("test-service")
                    .build();
            when(agentTraceRepository.findByTraceIdIn(List.of("trace-001"))).thenReturn(List.of(trace));

            // when
            final Page<ShadowTraceItemResponse> results = shadowResultService.getTracesWithShadowResults(pageable);

            // then
            assertThat(results.getContent()).hasSize(1);
            assertThat(results.getContent().get(0).getTraceId()).isEqualTo("trace-001");
            assertThat(results.getContent().get(0).getServiceName()).isEqualTo("test-service");
            assertThat(results.getContent().get(0).getShadowCount()).isEqualTo(1);
            assertThat(results.getContent().get(0).getLatestModelId()).isEqualTo("gpt-4");
        }

        @Test
        @DisplayName("should return empty page when no results")
        void should_ReturnEmptyPage_When_NoResults() {
            // given
            final Pageable pageable = PageRequest.of(0, 10);
            when(shadowResultRepository.findAll()).thenReturn(Collections.emptyList());

            // when
            final Page<ShadowTraceItemResponse> results = shadowResultService.getTracesWithShadowResults(pageable);

            // then
            assertThat(results.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getResultsForConfig()")
    class GetResultsForConfig {

        @Test
        @DisplayName("should return paginated results for config with evaluations")
        void should_ReturnPaginatedResultsWithEvaluations() {
            // given
            final ShadowResult result = ShadowResult.builder()
                    .id(1L)
                    .sourceSpanId("span-001")
                    .shadowConfigId(10L)
                    .modelId("gpt-4")
                    .status("SUCCESS")
                    .build();
            when(shadowResultRepository.findByShadowConfigId(10L)).thenReturn(List.of(result));

            final ShadowResultResponse response = ShadowResultResponse.builder()
                    .id(1L)
                    .sourceSpanId("span-001")
                    .build();
            when(shadowMapper.toResultResponseList(List.of(result))).thenReturn(List.of(response));
            when(shadowEvaluationRepository.findByShadowResultId(1L)).thenReturn(Collections.emptyList());

            final Pageable pageable = PageRequest.of(0, 10);

            // when
            final Page<ShadowResultResponse> page = shadowResultService.getResultsForConfig(10L, pageable);

            // then
            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return empty page when offset exceeds results")
        void should_ReturnEmptyPage_When_OffsetExceedsResults() {
            // given
            final ShadowResult result = ShadowResult.builder()
                    .id(1L)
                    .sourceSpanId("span-001")
                    .shadowConfigId(10L)
                    .modelId("gpt-4")
                    .status("SUCCESS")
                    .build();
            when(shadowResultRepository.findByShadowConfigId(10L)).thenReturn(List.of(result));

            final ShadowResultResponse response =
                    ShadowResultResponse.builder().id(1L).build();
            when(shadowMapper.toResultResponseList(List.of(result))).thenReturn(List.of(response));
            when(shadowEvaluationRepository.findByShadowResultId(1L)).thenReturn(Collections.emptyList());

            final Pageable pageable = PageRequest.of(5, 10);

            // when
            final Page<ShadowResultResponse> page = shadowResultService.getResultsForConfig(10L, pageable);

            // then
            assertThat(page.getContent()).isEmpty();
        }
    }
}
