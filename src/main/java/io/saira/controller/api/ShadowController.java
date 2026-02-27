package io.saira.controller.api;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import io.saira.dto.ShadowExecuteRequest;
import io.saira.dto.ShadowResultResponse;
import io.saira.dto.ShadowTraceItemResponse;
import io.saira.entity.ShadowEvaluation;
import io.saira.service.shadow.ShadowEvaluationService;
import io.saira.service.shadow.ShadowExecutionService;
import io.saira.service.shadow.ShadowResultService;
import jakarta.validation.Valid;

/**
 * REST контроллер для shadow-вызовов и результатов.
 *
 * <p>Предоставляет API для ручного shadow-тестирования и получения
 * результатов по span, trace и конфигурации.
 */
@RestController
@RequestMapping("/api/v1/shadow")
public class ShadowController {

    /** Сервис выполнения shadow-вызовов. */
    private final ShadowExecutionService shadowExecutionService;

    /** Сервис хранения и получения shadow-результатов. */
    private final ShadowResultService shadowResultService;

    /** Сервис оценки shadow-результатов (null если метрики недоступны). */
    @Nullable private final ShadowEvaluationService shadowEvaluationService;

    /**
     * Конструктор с опциональной зависимостью от ShadowEvaluationService.
     *
     * @param shadowExecutionService  сервис выполнения shadow-вызовов
     * @param shadowResultService     сервис хранения результатов
     * @param shadowEvaluationService сервис оценки (может быть null)
     */
    public ShadowController(
            final ShadowExecutionService shadowExecutionService,
            final ShadowResultService shadowResultService,
            @Autowired(required = false) @Nullable final ShadowEvaluationService shadowEvaluationService) {
        this.shadowExecutionService = shadowExecutionService;
        this.shadowResultService = shadowResultService;
        this.shadowEvaluationService = shadowEvaluationService;
    }

    /**
     * Выполнить ручной shadow-тест для указанного span-а.
     *
     * @param request параметры shadow-теста (spanId, modelId, providerName)
     * @return результат shadow-вызова (201 Created)
     */
    @PostMapping("/execute")
    public ResponseEntity<ShadowResultResponse> execute(@Valid @RequestBody final ShadowExecuteRequest request) {
        final ShadowResultResponse response = shadowExecutionService.executeManual(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Получить shadow-результаты для указанного span-а.
     *
     * @param spanId OpenTelemetry span ID
     * @return список shadow-результатов с оценками
     */
    @GetMapping("/results/span/{spanId}")
    public List<ShadowResultResponse> getResultsBySpan(@PathVariable final String spanId) {
        return shadowResultService.getResultsForSpan(spanId);
    }

    /**
     * Получить shadow-результаты для всех span-ов указанного трейса.
     *
     * @param traceId OpenTelemetry trace ID
     * @return список shadow-результатов с оценками
     */
    @GetMapping("/results/trace/{traceId}")
    public List<ShadowResultResponse> getResultsByTrace(@PathVariable final String traceId) {
        return shadowResultService.getResultsForTrace(traceId);
    }

    /**
     * Получить список трейсов, имеющих shadow-результаты (с пагинацией).
     *
     * @param page номер страницы (начиная с 0)
     * @param size размер страницы
     * @return страница shadow-результатов
     */
    @GetMapping("/traces")
    public Page<ShadowTraceItemResponse> getShadowTraces(
            @RequestParam(defaultValue = "0") final int page, @RequestParam(defaultValue = "20") final int size) {
        return shadowResultService.getTracesWithShadowResults(PageRequest.of(page, size));
    }

    /**
     * Получить оценку для указанного shadow-результата.
     *
     * @param resultId ID shadow-результата
     * @return оценка (200 OK) или 404 если не найдена
     */
    @GetMapping("/results/{resultId}/evaluation")
    public ResponseEntity<ShadowEvaluation> getEvaluation(@PathVariable final Long resultId) {
        if (shadowEvaluationService == null) {
            return ResponseEntity.notFound().build();
        }
        return shadowEvaluationService
                .getEvaluationForResult(resultId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
