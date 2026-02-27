package io.saira.service.trace;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import io.saira.dto.SpanResponse;
import io.saira.dto.TraceDetailResponse;
import io.saira.dto.TraceListItemResponse;
import io.saira.entity.AgentSpan;
import io.saira.entity.AgentTrace;
import io.saira.repository.AgentSpanRepository;
import io.saira.repository.AgentTraceRepository;
import lombok.RequiredArgsConstructor;

/**
 * Сервис для работы с трейсами агентов.
 * Используется в TraceController для REST API.
 */
@Service
@RequiredArgsConstructor
public class TraceService {

    /** Репозиторий для работы с трейсами в БД. */
    private final AgentTraceRepository traceRepository;

    /** Репозиторий для работы со span-ами в БД. */
    private final AgentSpanRepository spanRepository;

    /**
     * Получить список трейсов с пагинацией.
     *
     * @param pageable параметры пагинации
     * @return страница с элементами списка трейсов
     */
    public Page<TraceListItemResponse> getTraces(final Pageable pageable) {
        return traceRepository.findAllByOrderByStartedAtDesc(pageable).map(this::toListItem);
    }

    /**
     * Получить детальную информацию о трейсе по его trace ID.
     *
     * @param traceId OpenTelemetry trace ID
     * @return детализированная информация о трейсе со списком спанов
     * @throws ResponseStatusException если трейс не найден (404)
     */
    public TraceDetailResponse getTraceDetail(final String traceId) {
        final AgentTrace trace = traceRepository
                .findByTraceId(traceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace not found: " + traceId));
        final List<AgentSpan> spans = spanRepository.findByTraceIdOrderByStartedAtAsc(traceId);
        return toDetail(trace, spans);
    }

    /** Преобразует сущность AgentTrace в элемент списка трейсов. */
    private TraceListItemResponse toListItem(final AgentTrace trace) {
        return new TraceListItemResponse(
                trace.getTraceId(),
                trace.getServiceName(),
                trace.getFpId(),
                trace.getFpModuleId(),
                trace.getStartedAt(),
                trace.getEndedAt(),
                trace.getSpanCount(),
                trace.getStatus(),
                trace.getCreatedAt());
    }

    /** Преобразует сущность AgentSpan в DTO спана. */
    private SpanResponse toSpanResponse(final AgentSpan span) {
        return new SpanResponse(
                span.getSpanId(),
                span.getParentSpanId(),
                span.getOperationName(),
                span.getSpanKind(),
                span.getStartedAt(),
                span.getDurationMicros(),
                span.getStatusCode(),
                span.getHttpUrl(),
                span.getHttpMethod(),
                span.getHttpStatus(),
                span.getClientName(),
                span.getRequestBody(),
                span.getResponseBody(),
                span.getOutcome());
    }

    /** Преобразует сущность AgentTrace и список спанов в детальный DTO. */
    private TraceDetailResponse toDetail(final AgentTrace trace, final List<AgentSpan> spans) {
        final List<SpanResponse> spanResponses =
                spans.stream().map(this::toSpanResponse).toList();
        return new TraceDetailResponse(
                trace.getTraceId(),
                trace.getServiceName(),
                trace.getFpId(),
                trace.getFpModuleId(),
                trace.getStartedAt(),
                trace.getEndedAt(),
                trace.getSpanCount(),
                trace.getStatus(),
                trace.getCreatedAt(),
                spanResponses);
    }
}
