package io.saira.service.shadow;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис хранения и получения результатов shadow-вызовов.
 *
 * <p>Обеспечивает сохранение результатов, обогащение данными из evaluation,
 * и запросы по span, trace и config.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowResultService {

    /** Репозиторий shadow-результатов. */
    private final ShadowResultRepository shadowResultRepository;

    /** Репозиторий оценок shadow-результатов. */
    private final ShadowEvaluationRepository shadowEvaluationRepository;

    /** Репозиторий span-ов для получения span-ов трейса. */
    private final AgentSpanRepository agentSpanRepository;

    /** Репозиторий трейсов для получения serviceName. */
    private final AgentTraceRepository agentTraceRepository;

    /** MapStruct маппер для shadow DTO. */
    private final ShadowMapper shadowMapper;

    /**
     * Сохраняет результат shadow-вызова.
     *
     * @param result результат для сохранения
     * @return сохранённый результат с заполненным id
     */
    @Transactional
    public ShadowResult save(final ShadowResult result) {
        Assert.notNull(result, "ShadowResult cannot be null");
        final ShadowResult saved = shadowResultRepository.save(result);
        log.debug(
                "Сохранён shadow-результат id={}, span={}, model={}",
                saved.getId(),
                saved.getSourceSpanId(),
                saved.getModelId());
        return saved;
    }

    /**
     * Возвращает результаты shadow-вызовов для указанного span-а.
     * Обогащает ответы данными из evaluation (similarity, correctness).
     *
     * @param spanId OpenTelemetry span ID
     * @return список результатов с оценками
     */
    @Transactional(readOnly = true)
    public List<ShadowResultResponse> getResultsForSpan(final String spanId) {
        Assert.hasText(spanId, "spanId cannot be blank");

        final List<ShadowResult> results = shadowResultRepository.findBySourceSpanId(spanId);
        if (results.isEmpty()) {
            return Collections.emptyList();
        }

        final List<ShadowResultResponse> responses = shadowMapper.toResultResponseList(results);
        enrichWithEvaluations(results, responses);
        return responses;
    }

    /**
     * Возвращает результаты shadow-вызовов для всех span-ов указанного трейса.
     * Получает список span-ов трейса, затем ищет shadow-результаты по их ID.
     *
     * @param traceId OpenTelemetry trace ID
     * @return список результатов с оценками
     */
    @Transactional(readOnly = true)
    public List<ShadowResultResponse> getResultsForTrace(final String traceId) {
        Assert.hasText(traceId, "traceId cannot be blank");

        final List<AgentSpan> spans = agentSpanRepository.findByTraceIdOrderByStartedAtAsc(traceId);
        if (spans.isEmpty()) {
            return Collections.emptyList();
        }

        final List<String> spanIds = spans.stream().map(AgentSpan::getSpanId).toList();
        final List<ShadowResult> results = shadowResultRepository.findBySourceSpanIdIn(spanIds);
        if (results.isEmpty()) {
            return Collections.emptyList();
        }

        final List<ShadowResultResponse> responses = shadowMapper.toResultResponseList(results);
        enrichWithEvaluations(results, responses);
        return responses;
    }

    /**
     * Возвращает результаты shadow-вызовов для указанной конфигурации (с пагинацией).
     *
     * @param configId идентификатор shadow-конфигурации
     * @param pageable параметры пагинации
     * @return страница результатов
     */
    @Transactional(readOnly = true)
    public Page<ShadowResultResponse> getResultsForConfig(final Long configId, final Pageable pageable) {
        Assert.notNull(configId, "configId cannot be null");

        final List<ShadowResult> allResults = shadowResultRepository.findByShadowConfigId(configId);
        final List<ShadowResultResponse> responses = shadowMapper.toResultResponseList(allResults);
        enrichWithEvaluations(allResults, responses);

        final int start = (int) pageable.getOffset();
        final int end = Math.min(start + pageable.getPageSize(), responses.size());
        if (start >= responses.size()) {
            return Page.empty(pageable);
        }

        final List<ShadowResultResponse> pageContent = responses.subList(start, end);
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, responses.size());
    }

    /**
     * Возвращает пагинированный список трейсов с агрегированными shadow-данными.
     *
     * @param pageable параметры пагинации
     * @return страница агрегированных shadow-трейсов
     */
    @Transactional(readOnly = true)
    public Page<ShadowTraceItemResponse> getTracesWithShadowResults(final Pageable pageable) {
        final List<ShadowResult> allResults = shadowResultRepository.findAll();
        if (allResults.isEmpty()) {
            return Page.empty(pageable);
        }

        // Собираем все sourceSpanId -> traceId
        final Set<String> spanIds =
                allResults.stream().map(ShadowResult::getSourceSpanId).collect(Collectors.toSet());
        final List<AgentSpan> spans = agentSpanRepository.findBySpanIdIn(new ArrayList<>(spanIds));
        final Map<String, String> spanToTrace =
                spans.stream().collect(Collectors.toMap(AgentSpan::getSpanId, AgentSpan::getTraceId, (a, b) -> a));

        // Группируем результаты по traceId
        final Map<String, List<ShadowResult>> resultsByTrace = new LinkedHashMap<>();
        for (final ShadowResult result : allResults) {
            final String traceId = spanToTrace.get(result.getSourceSpanId());
            if (traceId != null) {
                resultsByTrace.computeIfAbsent(traceId, k -> new ArrayList<>()).add(result);
            }
        }

        // Получаем serviceName для каждого трейса
        final Set<String> traceIds = resultsByTrace.keySet();
        final Map<String, String> traceToService =
                agentTraceRepository.findByTraceIdIn(new ArrayList<>(traceIds)).stream()
                        .collect(Collectors.toMap(AgentTrace::getTraceId, AgentTrace::getServiceName, (a, b) -> a));

        // Загружаем evaluations для всех результатов
        final Map<Long, ShadowEvaluation> evaluationMap = allResults.stream()
                .map(ShadowResult::getId)
                .flatMap(id -> shadowEvaluationRepository.findByShadowResultId(id).stream())
                .collect(Collectors.toMap(ShadowEvaluation::getShadowResultId, eval -> eval, (a, b) -> a));

        // Агрегируем по трейсу
        final List<ShadowTraceItemResponse> items = new ArrayList<>();
        for (final Map.Entry<String, List<ShadowResult>> entry : resultsByTrace.entrySet()) {
            final String traceId = entry.getKey();
            final List<ShadowResult> traceResults = entry.getValue();

            // Сортируем по executedAt desc чтобы найти последний
            traceResults.sort(
                    Comparator.comparing(ShadowResult::getExecutedAt, Comparator.nullsLast(Comparator.reverseOrder())));

            final ShadowResult latest = traceResults.get(0);

            // Ищем лучший балл среди evaluations
            java.math.BigDecimal bestScore = null;
            for (final ShadowResult r : traceResults) {
                final ShadowEvaluation eval = evaluationMap.get(r.getId());
                if (eval != null && eval.getSemanticSimilarity() != null) {
                    if (bestScore == null || eval.getSemanticSimilarity().compareTo(bestScore) > 0) {
                        bestScore = eval.getSemanticSimilarity();
                    }
                }
            }

            items.add(ShadowTraceItemResponse.builder()
                    .traceId(traceId)
                    .serviceName(traceToService.getOrDefault(traceId, "unknown"))
                    .shadowCount(traceResults.size())
                    .latestScore(bestScore)
                    .latestModelId(latest.getModelId())
                    .latestExecutedAt(latest.getExecutedAt())
                    .build());
        }

        // Сортируем по latestExecutedAt desc
        items.sort(Comparator.comparing(
                ShadowTraceItemResponse::getLatestExecutedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        // Пагинация
        final int start = (int) pageable.getOffset();
        final int end = Math.min(start + pageable.getPageSize(), items.size());
        if (start >= items.size()) {
            return Page.empty(pageable);
        }
        return new PageImpl<>(items.subList(start, end), pageable, items.size());
    }

    /**
     * Обогащает список DTO-ответов данными из оценок (semantic similarity, correctness).
     *
     * @param results   исходные сущности ShadowResult
     * @param responses DTO-ответы для обогащения
     */
    private void enrichWithEvaluations(final List<ShadowResult> results, final List<ShadowResultResponse> responses) {
        final List<Long> resultIds = results.stream().map(ShadowResult::getId).toList();

        final Map<Long, ShadowEvaluation> evaluationMap = resultIds.stream()
                .flatMap(id -> shadowEvaluationRepository.findByShadowResultId(id).stream())
                .collect(Collectors.toMap(ShadowEvaluation::getShadowResultId, eval -> eval, (a, b) -> a));

        for (final ShadowResultResponse response : responses) {
            final ShadowEvaluation evaluation = evaluationMap.get(response.getId());
            if (evaluation != null) {
                response.setSemanticSimilarity(evaluation.getSemanticSimilarity());
                response.setCorrectnessScore(evaluation.getCorrectnessScore());
            }
        }
    }
}
