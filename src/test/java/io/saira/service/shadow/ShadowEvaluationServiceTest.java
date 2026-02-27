package io.saira.service.shadow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.qa.solutions.metrics.general.AspectCriticMetric;
import ai.qa.solutions.metrics.response.SemanticSimilarityMetric;
import ai.qa.solutions.sample.Sample;
import io.saira.entity.AgentSpan;
import io.saira.entity.ShadowEvaluation;
import io.saira.entity.ShadowResult;
import io.saira.repository.ShadowEvaluationRepository;

/** Тесты сервиса оценки shadow-результатов. */
@ExtendWith(MockitoExtension.class)
class ShadowEvaluationServiceTest {

    @Mock
    private SemanticSimilarityMetric semanticSimilarityMetric;

    @Mock
    private AspectCriticMetric aspectCriticMetric;

    @Mock
    private ShadowEvaluationRepository shadowEvaluationRepository;

    @InjectMocks
    private ShadowEvaluationService shadowEvaluationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("should_SaveEvaluationWithScores_When_MetricsAvailable")
    void should_SaveEvaluationWithScores_When_MetricsAvailable() {
        // given
        ShadowEvaluationService service = new ShadowEvaluationService(
                semanticSimilarityMetric, aspectCriticMetric, shadowEvaluationRepository, objectMapper);

        ShadowResult result = ShadowResult.builder()
                .id(1L)
                .sourceSpanId("span-001")
                .shadowConfigId(10L)
                .modelId("gpt-4")
                .requestBody("What is Java?")
                .responseBody("Java is a programming language.")
                .status("SUCCESS")
                .build();

        AgentSpan originalSpan = AgentSpan.builder()
                .spanId("span-001")
                .traceId("trace-001")
                .operationName("POST /completions")
                .startedAt(java.time.Instant.now())
                .durationMicros(1000L)
                .responseBody("Java is an object-oriented programming language.")
                .build();

        when(semanticSimilarityMetric.singleTurnScore(
                        any(SemanticSimilarityMetric.SemanticSimilarityConfig.class), any(Sample.class)))
                .thenReturn(0.92);
        when(aspectCriticMetric.singleTurnScore(any(AspectCriticMetric.AspectCriticConfig.class), any(Sample.class)))
                .thenReturn(1.0);
        when(shadowEvaluationRepository.save(any(ShadowEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ShadowEvaluation evaluation = service.evaluateResult(result, originalSpan);

        // then
        assertThat(evaluation).isNotNull();
        assertThat(evaluation.getShadowResultId()).isEqualTo(1L);
        assertThat(evaluation.getSemanticSimilarity()).isEqualByComparingTo(new BigDecimal("0.9200"));
        assertThat(evaluation.getCorrectnessScore()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(evaluation.getEvaluationDetail()).isNotBlank();
        assertThat(evaluation.getEvaluatedAt()).isNotNull();

        verify(shadowEvaluationRepository).save(any(ShadowEvaluation.class));
    }

    @Test
    @DisplayName("should_SaveErrorEvaluation_When_MetricsNotAvailable")
    void should_SaveErrorEvaluation_When_MetricsNotAvailable() {
        // given
        ShadowEvaluationService service =
                new ShadowEvaluationService(null, null, shadowEvaluationRepository, objectMapper);

        ShadowResult result = ShadowResult.builder()
                .id(1L)
                .sourceSpanId("span-001")
                .shadowConfigId(10L)
                .modelId("gpt-4")
                .responseBody("some response")
                .status("SUCCESS")
                .build();

        AgentSpan originalSpan = AgentSpan.builder()
                .spanId("span-001")
                .traceId("trace-001")
                .operationName("POST /completions")
                .startedAt(java.time.Instant.now())
                .durationMicros(1000L)
                .responseBody("original response")
                .build();

        when(shadowEvaluationRepository.save(any(ShadowEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ShadowEvaluation evaluation = service.evaluateResult(result, originalSpan);

        // then
        assertThat(evaluation).isNotNull();
        assertThat(evaluation.getSemanticSimilarity()).isNull();
        assertThat(evaluation.getCorrectnessScore()).isNull();
        assertThat(evaluation.getEvaluationDetail()).contains("not available");
    }

    @Test
    @DisplayName("should_SaveErrorEvaluation_When_OriginalResponseEmpty")
    void should_SaveErrorEvaluation_When_OriginalResponseEmpty() {
        // given
        ShadowEvaluationService service = new ShadowEvaluationService(
                semanticSimilarityMetric, aspectCriticMetric, shadowEvaluationRepository, objectMapper);

        ShadowResult result = ShadowResult.builder()
                .id(2L)
                .sourceSpanId("span-002")
                .shadowConfigId(10L)
                .modelId("gpt-4")
                .responseBody("shadow response")
                .status("SUCCESS")
                .build();

        AgentSpan originalSpan = AgentSpan.builder()
                .spanId("span-002")
                .traceId("trace-001")
                .operationName("POST /completions")
                .startedAt(java.time.Instant.now())
                .durationMicros(1000L)
                .responseBody(null)
                .build();

        when(shadowEvaluationRepository.save(any(ShadowEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ShadowEvaluation evaluation = service.evaluateResult(result, originalSpan);

        // then
        assertThat(evaluation.getSemanticSimilarity()).isNull();
        assertThat(evaluation.getCorrectnessScore()).isNull();
        assertThat(evaluation.getEvaluationDetail()).contains("no response body");
    }

    @Test
    @DisplayName("should_SaveErrorEvaluation_When_ShadowResponseEmpty")
    void should_SaveErrorEvaluation_When_ShadowResponseEmpty() {
        // given
        ShadowEvaluationService service = new ShadowEvaluationService(
                semanticSimilarityMetric, aspectCriticMetric, shadowEvaluationRepository, objectMapper);

        ShadowResult result = ShadowResult.builder()
                .id(3L)
                .sourceSpanId("span-003")
                .shadowConfigId(10L)
                .modelId("gpt-4")
                .responseBody("")
                .status("SUCCESS")
                .build();

        AgentSpan originalSpan = AgentSpan.builder()
                .spanId("span-003")
                .traceId("trace-001")
                .operationName("POST /completions")
                .startedAt(java.time.Instant.now())
                .durationMicros(1000L)
                .responseBody("original response")
                .build();

        when(shadowEvaluationRepository.save(any(ShadowEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ShadowEvaluation evaluation = service.evaluateResult(result, originalSpan);

        // then
        assertThat(evaluation.getSemanticSimilarity()).isNull();
        assertThat(evaluation.getCorrectnessScore()).isNull();
        assertThat(evaluation.getEvaluationDetail()).contains("no response body");
    }

    @Test
    @DisplayName("should_SaveErrorEvaluation_When_MetricThrowsException")
    void should_SaveErrorEvaluation_When_MetricThrowsException() {
        // given
        ShadowEvaluationService service = new ShadowEvaluationService(
                semanticSimilarityMetric, aspectCriticMetric, shadowEvaluationRepository, objectMapper);

        ShadowResult result = ShadowResult.builder()
                .id(4L)
                .sourceSpanId("span-004")
                .shadowConfigId(10L)
                .modelId("gpt-4")
                .requestBody("test prompt")
                .responseBody("shadow response")
                .status("SUCCESS")
                .build();

        AgentSpan originalSpan = AgentSpan.builder()
                .spanId("span-004")
                .traceId("trace-001")
                .operationName("POST /completions")
                .startedAt(java.time.Instant.now())
                .durationMicros(1000L)
                .responseBody("original response")
                .build();

        when(semanticSimilarityMetric.singleTurnScore(
                        any(SemanticSimilarityMetric.SemanticSimilarityConfig.class), any(Sample.class)))
                .thenThrow(new RuntimeException("AI provider unavailable"));
        when(shadowEvaluationRepository.save(any(ShadowEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ShadowEvaluation evaluation = service.evaluateResult(result, originalSpan);

        // then
        assertThat(evaluation.getSemanticSimilarity()).isNull();
        assertThat(evaluation.getCorrectnessScore()).isNull();
        assertThat(evaluation.getEvaluationDetail()).contains("AI provider unavailable");
    }

    @Test
    @DisplayName("should_ReturnEvaluation_When_GetEvaluationForResultExists")
    void should_ReturnEvaluation_When_GetEvaluationForResultExists() {
        // given
        ShadowEvaluationService service = new ShadowEvaluationService(
                semanticSimilarityMetric, aspectCriticMetric, shadowEvaluationRepository, objectMapper);

        ShadowEvaluation evaluation = ShadowEvaluation.builder()
                .id(1L)
                .shadowResultId(10L)
                .semanticSimilarity(new BigDecimal("0.8500"))
                .correctnessScore(new BigDecimal("1.0000"))
                .build();
        when(shadowEvaluationRepository.findByShadowResultId(10L)).thenReturn(List.of(evaluation));

        // when
        Optional<ShadowEvaluation> found = service.getEvaluationForResult(10L);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getSemanticSimilarity()).isEqualByComparingTo(new BigDecimal("0.8500"));
    }

    @Test
    @DisplayName("should_ReturnEmpty_When_GetEvaluationForResultNotFound")
    void should_ReturnEmpty_When_GetEvaluationForResultNotFound() {
        // given
        ShadowEvaluationService service = new ShadowEvaluationService(
                semanticSimilarityMetric, aspectCriticMetric, shadowEvaluationRepository, objectMapper);

        when(shadowEvaluationRepository.findByShadowResultId(99L)).thenReturn(Collections.emptyList());

        // when
        Optional<ShadowEvaluation> found = service.getEvaluationForResult(99L);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("should_ReturnTrue_When_IsAvailableWithMetrics")
    void should_ReturnTrue_When_IsAvailableWithMetrics() {
        // given
        ShadowEvaluationService service =
                new ShadowEvaluationService(semanticSimilarityMetric, null, shadowEvaluationRepository, objectMapper);

        // when & then
        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("should_ReturnFalse_When_IsAvailableWithoutMetrics")
    void should_ReturnFalse_When_IsAvailableWithoutMetrics() {
        // given
        ShadowEvaluationService service =
                new ShadowEvaluationService(null, null, shadowEvaluationRepository, objectMapper);

        // when & then
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("should_ThrowException_When_ResultIsNull")
    void should_ThrowException_When_ResultIsNull() {
        // given
        ShadowEvaluationService service = new ShadowEvaluationService(
                semanticSimilarityMetric, aspectCriticMetric, shadowEvaluationRepository, objectMapper);

        AgentSpan span = AgentSpan.builder()
                .spanId("span-001")
                .traceId("trace-001")
                .operationName("POST /completions")
                .startedAt(java.time.Instant.now())
                .durationMicros(1000L)
                .build();

        // when & then
        assertThatThrownBy(() -> service.evaluateResult(null, span)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should_ThrowException_When_OriginalSpanIsNull")
    void should_ThrowException_When_OriginalSpanIsNull() {
        // given
        ShadowEvaluationService service = new ShadowEvaluationService(
                semanticSimilarityMetric, aspectCriticMetric, shadowEvaluationRepository, objectMapper);

        ShadowResult result = ShadowResult.builder()
                .id(1L)
                .sourceSpanId("span-001")
                .shadowConfigId(10L)
                .modelId("gpt-4")
                .build();

        // when & then
        assertThatThrownBy(() -> service.evaluateResult(result, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should_PassCorrectSampleToSemanticSimilarity")
    void should_PassCorrectSampleToSemanticSimilarity() {
        // given
        ShadowEvaluationService service = new ShadowEvaluationService(
                semanticSimilarityMetric, aspectCriticMetric, shadowEvaluationRepository, objectMapper);

        ShadowResult result = ShadowResult.builder()
                .id(5L)
                .sourceSpanId("span-005")
                .shadowConfigId(10L)
                .modelId("gpt-4")
                .requestBody("What is Spring Boot?")
                .responseBody("Spring Boot is a framework.")
                .status("SUCCESS")
                .build();

        AgentSpan originalSpan = AgentSpan.builder()
                .spanId("span-005")
                .traceId("trace-001")
                .operationName("POST /completions")
                .startedAt(java.time.Instant.now())
                .durationMicros(1000L)
                .responseBody("Spring Boot simplifies Java development.")
                .build();

        ArgumentCaptor<Sample> sampleCaptor = ArgumentCaptor.forClass(Sample.class);
        when(semanticSimilarityMetric.singleTurnScore(
                        any(SemanticSimilarityMetric.SemanticSimilarityConfig.class), sampleCaptor.capture()))
                .thenReturn(0.75);
        when(aspectCriticMetric.singleTurnScore(any(AspectCriticMetric.AspectCriticConfig.class), any(Sample.class)))
                .thenReturn(1.0);
        when(shadowEvaluationRepository.save(any(ShadowEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        service.evaluateResult(result, originalSpan);

        // then
        Sample capturedSample = sampleCaptor.getValue();
        assertThat(capturedSample.getUserInput()).isEqualTo("What is Spring Boot?");
        assertThat(capturedSample.getResponse()).isEqualTo("Spring Boot is a framework.");
        assertThat(capturedSample.getReference()).isEqualTo("Spring Boot simplifies Java development.");
    }
}
