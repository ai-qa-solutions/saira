package io.saira.service.ingest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.saira.dto.IngestResponse;
import io.saira.entity.AgentSpan;
import io.saira.entity.AgentTrace;
import io.saira.repository.AgentSpanRepository;
import io.saira.repository.AgentTraceRepository;
import io.saira.service.shadow.ShadowInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис ingestion -- ядро pipeline обработки OTel данных.
 *
 * <p>Парсит {@link ExportTraceServiceRequest}, фильтрует span-ы через {@link SpanFilter},
 * извлекает структурированные поля и сохраняет в базу данных.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    /** Репозиторий для работы с трейсами агентов. */
    private final AgentTraceRepository traceRepository;

    /** Репозиторий для работы со span-ами агентов. */
    private final AgentSpanRepository spanRepository;

    /** Двухуровневый фильтр span-ов. */
    private final SpanFilter spanFilter;

    /** Реестр метрик Micrometer. */
    private final MeterRegistry meterRegistry;

    /** Перехватчик shadow-вызовов (null если shadow отключён). */
    @Autowired(required = false)
    @Nullable private ShadowInterceptor shadowInterceptor;

    /**
     * Обработка batch-а OTel данных.
     *
     * <p>Pipeline: парсинг ResourceSpans -> извлечение атрибутов -> фильтрация ->
     * построение сущностей -> дедупликация -> сохранение.
     *
     * @param request ExportTraceServiceRequest (из REST JSON или Kafka protobuf)
     * @return статистика обработки
     */
    @Transactional
    public IngestResponse ingest(final ExportTraceServiceRequest request) {
        Assert.notNull(request, "ExportTraceServiceRequest must not be null");

        final List<ResourceSpans> resourceSpansList = request.getResourceSpansList();
        log.debug("Ingesting batch with {} ResourceSpans", resourceSpansList.size());

        final IngestAccumulator accumulator = new IngestAccumulator();
        for (final ResourceSpans resourceSpans : resourceSpansList) {
            processResourceSpans(resourceSpans, accumulator);
        }

        flushAndRecordMetrics(accumulator);

        log.info(
                "Ingest complete: traces={}, spans={}, filtered={}",
                accumulator.traceIds.size(),
                accumulator.acceptedCount,
                accumulator.filteredCount);

        return IngestResponse.builder()
                .traceCount(accumulator.traceIds.size())
                .spanCount(accumulator.acceptedCount)
                .filteredOutCount(accumulator.filteredCount)
                .build();
    }

    /**
     * Обрабатывает один ResourceSpans -- извлекает service.name и делегирует обработку span-ов.
     *
     * @param resourceSpans ресурс со span-ами из OTel batch
     * @param accumulator   аккумулятор результатов обработки
     */
    private void processResourceSpans(final ResourceSpans resourceSpans, final IngestAccumulator accumulator) {
        final Resource resource = resourceSpans.getResource();
        final String serviceName = extractResourceAttribute(resource, "service.name");

        for (final ScopeSpans scopeSpans : resourceSpans.getScopeSpansList()) {
            processScopeSpans(scopeSpans, serviceName, accumulator);
        }
    }

    /**
     * Обрабатывает один ScopeSpans -- итерирует по span-ам и обрабатывает каждый.
     *
     * @param scopeSpans  набор span-ов из одного scope
     * @param serviceName имя сервиса из OTel Resource
     * @param accumulator аккумулятор результатов обработки
     */
    private void processScopeSpans(
            final ScopeSpans scopeSpans, final String serviceName, final IngestAccumulator accumulator) {
        for (final Span span : scopeSpans.getSpansList()) {
            processSpan(span, serviceName, accumulator);
        }
    }

    /**
     * Обрабатывает один span: извлекает атрибуты, фильтрует, строит сущности и сохраняет.
     *
     * @param span        OTel span
     * @param serviceName имя сервиса из OTel Resource
     * @param accumulator аккумулятор результатов обработки
     */
    private void processSpan(final Span span, final String serviceName, final IngestAccumulator accumulator) {
        final Map<String, String> attributes = extractAttributes(span);

        if (!spanFilter.shouldAccept(attributes)) {
            accumulator.filteredCount++;
            return;
        }

        final String spanId = hexSpanId(span);
        if (spanRepository.existsBySpanId(spanId)) {
            log.debug("Duplicate span {}, skipping", spanId);
            return;
        }

        final String traceId = hexTraceId(span);
        final AgentSpan agentSpan = buildAgentSpan(span, attributes, traceId, spanId);
        collectTraceData(traceId, serviceName, attributes, agentSpan.getStartedAt(), accumulator);

        accumulator.pendingSpans.add(agentSpan);
        accumulator.acceptedCount++;
    }

    /**
     * Собирает данные для upsert трейса: группирует span-ы по traceId.
     *
     * @param traceId     OpenTelemetry trace ID
     * @param serviceName имя сервиса из OTel Resource
     * @param attributes  атрибуты span-а
     * @param startedAt   время начала span-а
     * @param accumulator аккумулятор результатов обработки
     */
    private void collectTraceData(
            final String traceId,
            final String serviceName,
            final Map<String, String> attributes,
            final Instant startedAt,
            final IngestAccumulator accumulator) {
        final TraceData data = accumulator.traceIds.computeIfAbsent(traceId, k -> {
            final TraceData newData = new TraceData();
            newData.serviceName = serviceName;
            newData.fpId = attributes.get("cm.otel.fp.id");
            newData.fpModuleId = attributes.get("cm.otel.fp.module.id");
            newData.earliestStart = startedAt;
            return newData;
        });

        data.spanCount++;
        if (startedAt.isBefore(data.earliestStart)) {
            data.earliestStart = startedAt;
        }
        enrichFpFields(data, attributes);
    }

    /**
     * Дополняет fpId и fpModuleId из атрибутов, если они ещё не установлены.
     *
     * @param data       данные трейса
     * @param attributes атрибуты span-а
     */
    private void enrichFpFields(final TraceData data, final Map<String, String> attributes) {
        if (data.fpId == null) {
            data.fpId = attributes.get("cm.otel.fp.id");
        }
        if (data.fpModuleId == null) {
            data.fpModuleId = attributes.get("cm.otel.fp.module.id");
        }
    }

    /**
     * Извлекает атрибуты span-а в карту строк.
     *
     * <p>Все типы значений (string, int, bool, double, bytes) конвертируются в строковое
     * представление.
     *
     * @param span OTel span
     * @return карта атрибутов (key -> string value)
     */
    private Map<String, String> extractAttributes(final Span span) {
        final List<KeyValue> attributesList = span.getAttributesList();
        final Map<String, String> attributes = new HashMap<>(attributesList.size() + 1);
        for (final KeyValue kv : attributesList) {
            final String value = anyValueToString(kv.getValue());
            attributes.put(kv.getKey(), value);
        }
        // span.kind — proto enum field, не атрибут; добавляем вручную для фильтрации
        final String spanKind = spanKindToString(span.getKind());
        if (spanKind != null) {
            attributes.put("span.kind", spanKind);
        }
        return attributes;
    }

    /**
     * Конвертирует proto SpanKind enum в строку для фильтрации.
     *
     * @param kind proto SpanKind enum
     * @return строковое представление (client, server, internal, producer, consumer) или null
     */
    private String spanKindToString(final Span.SpanKind kind) {
        if (kind == Span.SpanKind.SPAN_KIND_CLIENT) {
            return "client";
        }
        if (kind == Span.SpanKind.SPAN_KIND_SERVER) {
            return "server";
        }
        if (kind == Span.SpanKind.SPAN_KIND_INTERNAL) {
            return "internal";
        }
        if (kind == Span.SpanKind.SPAN_KIND_PRODUCER) {
            return "producer";
        }
        if (kind == Span.SpanKind.SPAN_KIND_CONSUMER) {
            return "consumer";
        }
        return null;
    }

    /**
     * Конвертирует OTel {@link AnyValue} в строковое представление.
     *
     * @param value OTel значение атрибута
     * @return строковое представление значения
     */
    private String anyValueToString(final AnyValue value) {
        if (value.hasStringValue()) {
            return value.getStringValue();
        }
        if (value.hasIntValue()) {
            return String.valueOf(value.getIntValue());
        }
        if (value.hasBoolValue()) {
            return String.valueOf(value.getBoolValue());
        }
        if (value.hasDoubleValue()) {
            return String.valueOf(value.getDoubleValue());
        }
        if (value.hasBytesValue()) {
            return value.getBytesValue().toStringUtf8();
        }
        if (value.hasArrayValue()) {
            return value.getArrayValue().toString();
        }
        if (value.hasKvlistValue()) {
            return value.getKvlistValue().toString();
        }
        return "";
    }

    /**
     * Строит сущность {@link AgentSpan} из OTel span и извлечённых атрибутов.
     *
     * @param span       OTel span
     * @param attributes извлечённые атрибуты
     * @param traceId    hex-представление trace ID
     * @param spanId     hex-представление span ID
     * @return построенная сущность AgentSpan
     */
    private AgentSpan buildAgentSpan(
            final Span span, final Map<String, String> attributes, final String traceId, final String spanId) {
        return AgentSpan.builder()
                .traceId(traceId)
                .spanId(spanId)
                .parentSpanId(hexParentSpanId(span))
                .operationName(span.getName())
                .spanKind(attributes.getOrDefault("span.kind", "CLIENT"))
                .startedAt(toInstant(span.getStartTimeUnixNano()))
                .durationMicros((span.getEndTimeUnixNano() - span.getStartTimeUnixNano()) / 1000)
                .statusCode(span.getStatus().getCode().name())
                .httpUrl(attributes.get("http.url"))
                .httpMethod(attributes.get("method"))
                .httpStatus(attributes.get("status"))
                .clientName(attributes.get("client.name"))
                .requestBody(attributes.get("http.request.body"))
                .responseBody(attributes.get("http.response.body"))
                .outcome(attributes.get("outcome"))
                .build();
    }

    /**
     * Извлекает значение атрибута из OTel Resource по ключу.
     *
     * @param resource OTel Resource
     * @param key      ключ атрибута
     * @return значение атрибута или пустая строка если не найден
     */
    private String extractResourceAttribute(final Resource resource, final String key) {
        for (final KeyValue kv : resource.getAttributesList()) {
            if (key.equals(kv.getKey())) {
                return anyValueToString(kv.getValue());
            }
        }
        return "";
    }

    /**
     * Конвертирует ByteString trace ID в hex строку.
     *
     * @param span OTel span
     * @return hex-представление trace ID
     */
    private String hexTraceId(final Span span) {
        return bytesToHex(span.getTraceId().toByteArray());
    }

    /**
     * Конвертирует ByteString span ID в hex строку.
     *
     * @param span OTel span
     * @return hex-представление span ID
     */
    private String hexSpanId(final Span span) {
        return bytesToHex(span.getSpanId().toByteArray());
    }

    /**
     * Конвертирует ByteString parent span ID в hex строку.
     *
     * <p>Возвращает null если parent span ID пуст (корневой span).
     *
     * @param span OTel span
     * @return hex-представление parent span ID или null
     */
    private String hexParentSpanId(final Span span) {
        final byte[] bytes = span.getParentSpanId().toByteArray();
        if (bytes.length == 0) {
            return null;
        }
        return bytesToHex(bytes);
    }

    /**
     * Конвертирует массив байтов в hex строку.
     *
     * @param bytes массив байтов
     * @return hex-представление
     */
    private String bytesToHex(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Конвертирует наносекунды Unix epoch в {@link Instant}.
     *
     * @param nanos время в наносекундах с Unix epoch
     * @return соответствующий Instant
     */
    private Instant toInstant(final long nanos) {
        return Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L);
    }

    /**
     * Выполняет upsert трейса: обновляет существующий или создаёт новый.
     *
     * @param traceId     OpenTelemetry trace ID
     * @param serviceName имя сервиса из OTel Resource
     * @param fpId        идентификатор FP
     * @param fpModuleId  идентификатор модуля FP
     * @param startedAt   время начала самого раннего span-а
     * @param spanCount   количество span-ов в трейсе
     * @return сохранённый трейс
     */
    private AgentTrace upsertTrace(
            final String traceId,
            final String serviceName,
            final String fpId,
            final String fpModuleId,
            final Instant startedAt,
            final int spanCount) {
        return traceRepository
                .findByTraceId(traceId)
                .map(existing -> updateExistingTrace(existing, startedAt, spanCount))
                .orElseGet(() -> createNewTrace(traceId, serviceName, fpId, fpModuleId, startedAt, spanCount));
    }

    /**
     * Обновляет существующий трейс: увеличивает счётчик span-ов и обновляет время начала.
     *
     * @param existing  существующий трейс
     * @param startedAt время начала нового span-а
     * @param spanCount количество новых span-ов
     * @return обновлённый трейс
     */
    private AgentTrace updateExistingTrace(final AgentTrace existing, final Instant startedAt, final int spanCount) {
        existing.setSpanCount(existing.getSpanCount() + spanCount);
        if (startedAt.isBefore(existing.getStartedAt())) {
            existing.setStartedAt(startedAt);
        }
        return traceRepository.save(existing);
    }

    /**
     * Создаёт новый трейс.
     *
     * @param traceId     OpenTelemetry trace ID
     * @param serviceName имя сервиса из OTel Resource
     * @param fpId        идентификатор FP
     * @param fpModuleId  идентификатор модуля FP
     * @param startedAt   время начала самого раннего span-а
     * @param spanCount   количество span-ов
     * @return созданный трейс
     */
    private AgentTrace createNewTrace(
            final String traceId,
            final String serviceName,
            final String fpId,
            final String fpModuleId,
            final Instant startedAt,
            final int spanCount) {
        return traceRepository.save(AgentTrace.builder()
                .traceId(traceId)
                .serviceName(serviceName)
                .fpId(fpId)
                .fpModuleId(fpModuleId)
                .startedAt(startedAt)
                .spanCount(spanCount)
                .build());
    }

    /**
     * Записывает метрики ingestion в Micrometer.
     *
     * @param accumulator аккумулятор результатов обработки
     */
    /**
     * Сохраняет данные и записывает метрики.
     * Порядок: сначала traces (FK parent), потом spans.
     */
    private void flushAndRecordMetrics(final IngestAccumulator accumulator) {
        flushTraces(accumulator);
        flushSpans(accumulator);

        meterRegistry.counter("saira.ingest.traces").increment(accumulator.traceIds.size());
        meterRegistry.counter("saira.ingest.spans.accepted").increment(accumulator.acceptedCount);
        meterRegistry.counter("saira.ingest.spans.filtered").increment(accumulator.filteredCount);
    }

    /**
     * Сохраняет все pending span-ы в базу данных и вызывает shadow-перехватчик.
     */
    private void flushSpans(final IngestAccumulator accumulator) {
        for (final AgentSpan agentSpan : accumulator.pendingSpans) {
            spanRepository.save(agentSpan);
            interceptForShadow(agentSpan, accumulator);
        }
    }

    /**
     * Вызывает shadow-перехватчик для сохранённого span-а.
     * Определяет serviceName из данных трейса в аккумуляторе.
     *
     * @param agentSpan   сохранённый span
     * @param accumulator аккумулятор с данными трейсов
     */
    private void interceptForShadow(final AgentSpan agentSpan, final IngestAccumulator accumulator) {
        if (shadowInterceptor == null) {
            return;
        }
        final TraceData traceData = accumulator.traceIds.get(agentSpan.getTraceId());
        final String serviceName = traceData != null ? traceData.serviceName : "";
        shadowInterceptor.interceptSpan(agentSpan, serviceName);
    }

    /**
     * Сохраняет все собранные данные трейсов в базу данных.
     *
     * @param accumulator аккумулятор результатов обработки
     */
    private void flushTraces(final IngestAccumulator accumulator) {
        for (final Map.Entry<String, TraceData> entry : accumulator.traceIds.entrySet()) {
            final String traceId = entry.getKey();
            final TraceData data = entry.getValue();
            upsertTrace(traceId, data.serviceName, data.fpId, data.fpModuleId, data.earliestStart, data.spanCount);
        }
    }

    /**
     * Внутренний аккумулятор для сбора статистики обработки batch-а.
     */
    private static class IngestAccumulator {

        /** Данные трейсов, сгруппированные по trace ID. */
        final Map<String, TraceData> traceIds = new LinkedHashMap<>();

        /** Span-ы, ожидающие сохранения (после flush traces). */
        final List<AgentSpan> pendingSpans = new ArrayList<>();

        /** Счётчик принятых span-ов. */
        int acceptedCount;

        /** Счётчик отфильтрованных span-ов. */
        int filteredCount;
    }

    /**
     * Данные трейса, собранные из span-ов.
     */
    private static class TraceData {

        /** Имя сервиса из OTel Resource. */
        String serviceName;

        /** Идентификатор FP. */
        String fpId;

        /** Идентификатор модуля FP. */
        String fpModuleId;

        /** Время начала самого раннего span-а в трейсе. */
        Instant earliestStart;

        /** Количество span-ов в трейсе. */
        int spanCount;
    }
}
