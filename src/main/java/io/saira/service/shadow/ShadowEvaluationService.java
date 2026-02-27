package io.saira.service.shadow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.qa.solutions.metrics.general.AspectCriticMetric;
import ai.qa.solutions.metrics.response.SemanticSimilarityMetric;
import ai.qa.solutions.sample.Sample;
import io.saira.entity.AgentSpan;
import io.saira.entity.ShadowEvaluation;
import io.saira.entity.ShadowResult;
import io.saira.repository.ShadowEvaluationRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис оценки качества shadow-результатов через spring-ai-ragas метрики.
 *
 * <p>Использует SemanticSimilarity для сравнения семантической близости
 * оригинального и shadow ответов, и AspectCritic для оценки корректности
 * (сохранение интента оригинального запроса).
 *
 * <p>Зависимости от spring-ai-ragas метрик опциональны: если AI-провайдер
 * не настроен (например, в тестовом профиле), сервис создаётся, но метрики
 * не вычисляются.
 */
@Slf4j
@Service
public class ShadowEvaluationService {

    /** Масштаб для хранения оценок (4 знака после запятой). */
    private static final int SCORE_SCALE = 4;

    /** Определение критерия корректности для AspectCritic. */
    private static final String CORRECTNESS_DEFINITION =
            "Does the shadow response preserve the intent and meaning of the original request? "
                    + "Return 1 if the shadow response correctly addresses the same question/task as the original, "
                    + "otherwise return 0.";

    /** Метрика семантической близости (null если AI-провайдер не настроен). */
    @Nullable private final SemanticSimilarityMetric semanticSimilarityMetric;

    /** Метрика корректности (null если AI-провайдер не настроен). */
    @Nullable private final AspectCriticMetric aspectCriticMetric;

    /** Репозиторий оценок shadow-результатов. */
    private final ShadowEvaluationRepository shadowEvaluationRepository;

    /** Jackson ObjectMapper для сериализации деталей оценки. */
    private final ObjectMapper objectMapper;

    /**
     * Конструктор с опциональными зависимостями от spring-ai-ragas метрик.
     *
     * @param semanticSimilarityMetric метрика семантической близости (может быть null)
     * @param aspectCriticMetric       метрика корректности (может быть null)
     * @param shadowEvaluationRepository репозиторий оценок
     * @param objectMapper             Jackson ObjectMapper
     */
    public ShadowEvaluationService(
            @Autowired(required = false) @Nullable final SemanticSimilarityMetric semanticSimilarityMetric,
            @Autowired(required = false) @Nullable final AspectCriticMetric aspectCriticMetric,
            final ShadowEvaluationRepository shadowEvaluationRepository,
            final ObjectMapper objectMapper) {
        this.semanticSimilarityMetric = semanticSimilarityMetric;
        this.aspectCriticMetric = aspectCriticMetric;
        this.shadowEvaluationRepository = shadowEvaluationRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Оценивает shadow-результат по метрикам семантической близости и корректности.
     *
     * <p>Сравнивает responseBody shadow-результата с responseBody оригинального span-а.
     * При ошибке оценки сохраняет результат с null-скорами и описанием ошибки.
     *
     * @param result       shadow-результат для оценки
     * @param originalSpan оригинальный span с эталонным ответом
     * @return сохранённая оценка с заполненными скорами
     */
    @Transactional
    public ShadowEvaluation evaluateResult(final ShadowResult result, final AgentSpan originalSpan) {
        Assert.notNull(result, "ShadowResult cannot be null");
        Assert.notNull(originalSpan, "originalSpan cannot be null");

        if (!isAvailable()) {
            log.warn("Evaluation metrics не доступны, пропуск оценки для result={}", result.getId());
            return saveErrorEvaluation(
                    result.getId(), "Evaluation metrics are not available (no AI provider configured)");
        }

        final String originalResponse = originalSpan.getResponseBody();
        final String shadowResponse = result.getResponseBody();

        if (originalResponse == null || originalResponse.isBlank()) {
            return saveErrorEvaluation(result.getId(), "Original span has no response body");
        }
        if (shadowResponse == null || shadowResponse.isBlank()) {
            return saveErrorEvaluation(result.getId(), "Shadow result has no response body");
        }

        final String userInput = result.getRequestBody() != null ? result.getRequestBody() : "";

        try {
            final BigDecimal similarityScore = computeSemanticSimilarity(userInput, shadowResponse, originalResponse);
            final BigDecimal correctnessScore = computeCorrectness(userInput, shadowResponse);
            final String evaluationDetail = buildEvaluationDetail(similarityScore, correctnessScore, null);

            final ShadowEvaluation evaluation = ShadowEvaluation.builder()
                    .shadowResultId(result.getId())
                    .semanticSimilarity(similarityScore)
                    .correctnessScore(correctnessScore)
                    .evaluationDetail(evaluationDetail)
                    .evaluatedAt(Instant.now())
                    .build();

            ShadowEvaluation saved = shadowEvaluationRepository.save(evaluation);
            log.info(
                    "Оценка shadow-результата id={}: similarity={}, correctness={}",
                    result.getId(),
                    similarityScore,
                    correctnessScore);
            return saved;
        } catch (Exception e) {
            log.error("Ошибка оценки shadow-результата id={}: {}", result.getId(), e.getMessage(), e);
            return saveErrorEvaluation(result.getId(), e.getMessage());
        }
    }

    /**
     * Возвращает оценку для указанного shadow-результата.
     *
     * @param resultId ID shadow-результата
     * @return оценка или empty, если не найдена
     */
    @Transactional(readOnly = true)
    public Optional<ShadowEvaluation> getEvaluationForResult(final Long resultId) {
        Assert.notNull(resultId, "resultId cannot be null");
        List<ShadowEvaluation> evaluations = shadowEvaluationRepository.findByShadowResultId(resultId);
        return evaluations.isEmpty() ? Optional.empty() : Optional.of(evaluations.getFirst());
    }

    /**
     * Проверяет доступность хотя бы одной метрики оценки.
     *
     * @return true если доступна SemanticSimilarity или AspectCritic
     */
    public boolean isAvailable() {
        return semanticSimilarityMetric != null || aspectCriticMetric != null;
    }

    /**
     * Вычисляет семантическую близость shadow-ответа к оригиналу.
     *
     * @param userInput        текст запроса пользователя
     * @param shadowResponse   ответ shadow-модели
     * @param originalResponse эталонный ответ (оригинальный)
     * @return оценка от 0.0000 до 1.0000, или null если метрика недоступна
     */
    @Nullable private BigDecimal computeSemanticSimilarity(
            final String userInput, final String shadowResponse, final String originalResponse) {
        if (semanticSimilarityMetric == null) {
            return null;
        }

        Sample sample = Sample.builder()
                .userInput(userInput)
                .response(shadowResponse)
                .reference(originalResponse)
                .build();

        SemanticSimilarityMetric.SemanticSimilarityConfig config =
                SemanticSimilarityMetric.SemanticSimilarityConfig.defaultConfig();
        Double score = semanticSimilarityMetric.singleTurnScore(config, sample);
        return score != null ? BigDecimal.valueOf(score).setScale(SCORE_SCALE, RoundingMode.HALF_UP) : null;
    }

    /**
     * Вычисляет корректность shadow-ответа через AspectCritic.
     *
     * @param userInput      текст запроса пользователя
     * @param shadowResponse ответ shadow-модели
     * @return оценка 0.0000 или 1.0000 (binary), или null если метрика недоступна
     */
    @Nullable private BigDecimal computeCorrectness(final String userInput, final String shadowResponse) {
        if (aspectCriticMetric == null) {
            return null;
        }

        Sample sample =
                Sample.builder().userInput(userInput).response(shadowResponse).build();

        AspectCriticMetric.AspectCriticConfig config = AspectCriticMetric.AspectCriticConfig.builder()
                .definition(CORRECTNESS_DEFINITION)
                .build();
        Double score = aspectCriticMetric.singleTurnScore(config, sample);
        return score != null ? BigDecimal.valueOf(score).setScale(SCORE_SCALE, RoundingMode.HALF_UP) : null;
    }

    /**
     * Формирует JSON-строку с деталями оценки.
     *
     * @param similarity  оценка семантической близости (может быть null)
     * @param correctness оценка корректности (может быть null)
     * @param error       сообщение об ошибке (может быть null)
     * @return JSON-строка с деталями
     */
    private String buildEvaluationDetail(
            @Nullable final BigDecimal similarity,
            @Nullable final BigDecimal correctness,
            @Nullable final String error) {
        Map<String, Object> detail = new HashMap<>();
        if (similarity != null) {
            detail.put("semanticSimilarity", similarity);
        }
        if (correctness != null) {
            detail.put("correctnessScore", correctness);
        }
        if (error != null) {
            detail.put("error", error);
        }
        detail.put("evaluatedAt", Instant.now().toString());

        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            log.warn("Не удалось сериализовать детали оценки: {}", e.getMessage());
            return "{\"error\":\"Failed to serialize evaluation detail\"}";
        }
    }

    /**
     * Сохраняет оценку с ошибкой (null-скоры и описание ошибки).
     *
     * @param resultId     ID shadow-результата
     * @param errorMessage сообщение об ошибке
     * @return сохранённая оценка с ошибкой
     */
    private ShadowEvaluation saveErrorEvaluation(final Long resultId, final String errorMessage) {
        final String evaluationDetail = buildEvaluationDetail(null, null, errorMessage);
        final ShadowEvaluation evaluation = ShadowEvaluation.builder()
                .shadowResultId(resultId)
                .semanticSimilarity(null)
                .correctnessScore(null)
                .evaluationDetail(evaluationDetail)
                .evaluatedAt(Instant.now())
                .build();
        return shadowEvaluationRepository.save(evaluation);
    }
}
