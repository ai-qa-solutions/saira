package io.saira.service.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.protobuf.ByteString;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.saira.config.IngestProperties;
import io.saira.dto.IngestResponse;
import io.saira.entity.AgentSpan;
import io.saira.entity.AgentTrace;
import io.saira.repository.AgentSpanRepository;
import io.saira.repository.AgentTraceRepository;

/** Unit-тесты сервиса ingestion OTel данных. */
@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

    @Mock
    private AgentTraceRepository traceRepository;

    @Mock
    private AgentSpanRepository spanRepository;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private IngestService ingestService;

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);

        final IngestProperties properties = new IngestProperties();
        properties.setUrlPatterns(List.of("/completions", "/embeddings", "/mcp"));
        final SpanFilter spanFilter = new SpanFilter(properties);

        ingestService = new IngestService(traceRepository, spanRepository, spanFilter, meterRegistry);
    }

    @Test
    @DisplayName("Сохраняет HTTP client span, если URL содержит /completions")
    void should_SaveHttpClientSpan_When_UrlMatchesPattern() {
        // given
        final String traceIdHex = "0123456789abcdef0123456789abcdef";
        final String spanIdHex = "0123456789abcdef";
        final Span httpClientSpan = buildHttpClientSpan(
                traceIdHex, spanIdHex, "POST /v1/completions", "https://api.openai.com/v1/completions", "POST");

        final ExportTraceServiceRequest request = buildRequest("test-service", httpClientSpan);

        when(spanRepository.existsBySpanId(spanIdHex)).thenReturn(false);
        when(traceRepository.findByTraceId(traceIdHex)).thenReturn(Optional.empty());
        when(traceRepository.save(any(AgentTrace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        final IngestResponse response = ingestService.ingest(request);

        // then
        assertThat(response.getSpanCount()).isEqualTo(1);
        assertThat(response.getFilteredOutCount()).isZero();
        assertThat(response.getTraceCount()).isEqualTo(1);

        final ArgumentCaptor<AgentSpan> spanCaptor = ArgumentCaptor.forClass(AgentSpan.class);
        verify(spanRepository).save(spanCaptor.capture());

        final AgentSpan savedSpan = spanCaptor.getValue();
        assertThat(savedSpan.getTraceId()).isEqualTo(traceIdHex);
        assertThat(savedSpan.getSpanId()).isEqualTo(spanIdHex);
        assertThat(savedSpan.getHttpUrl()).isEqualTo("https://api.openai.com/v1/completions");
        assertThat(savedSpan.getHttpMethod()).isEqualTo("POST");
        assertThat(savedSpan.getHttpStatus()).isEqualTo("200");
        assertThat(savedSpan.getClientName()).isEqualTo("api.openai.com");
    }

    @Test
    @DisplayName("Фильтрует server span и не сохраняет")
    void should_FilterOutNonHttpSpans() {
        // given
        final Span serverSpan =
                buildSpanWithKind("0123456789abcdef0123456789abcdef", "abcdef0123456789", "GET /api/health", "server");

        final ExportTraceServiceRequest request = buildRequest("test-service", serverSpan);

        // when
        final IngestResponse response = ingestService.ingest(request);

        // then
        assertThat(response.getSpanCount()).isZero();
        assertThat(response.getFilteredOutCount()).isEqualTo(1);
        verify(spanRepository, never()).save(any(AgentSpan.class));
    }

    @Test
    @DisplayName("Фильтрует HTTP client span с URL, не входящим в whitelist")
    void should_FilterOutNonMatchingUrl() {
        // given
        final Span oauthSpan = buildHttpClientSpan(
                "0123456789abcdef0123456789abcdef",
                "abcdef0123456789",
                "POST /oauth/token",
                "https://auth.example.com/oauth/token",
                "POST");

        final ExportTraceServiceRequest request = buildRequest("test-service", oauthSpan);

        // when
        final IngestResponse response = ingestService.ingest(request);

        // then
        assertThat(response.getSpanCount()).isZero();
        assertThat(response.getFilteredOutCount()).isEqualTo(1);
        verify(spanRepository, never()).save(any(AgentSpan.class));
    }

    @Test
    @DisplayName("Пропускает дубликат span по spanId")
    void should_DeduplicateBySpanId() {
        // given
        final String traceIdHex = "0123456789abcdef0123456789abcdef";
        final String spanIdHex = "0123456789abcdef";
        final Span httpClientSpan = buildHttpClientSpan(
                traceIdHex, spanIdHex, "POST /v1/completions", "https://api.openai.com/v1/completions", "POST");

        final ExportTraceServiceRequest request = buildRequest("test-service", httpClientSpan);

        when(spanRepository.existsBySpanId(spanIdHex)).thenReturn(true);

        // when
        final IngestResponse response = ingestService.ingest(request);

        // then
        assertThat(response.getSpanCount()).isZero();
        verify(spanRepository, never()).save(any(AgentSpan.class));
    }

    /**
     * Строит ExportTraceServiceRequest с указанным именем сервиса и span-ами.
     *
     * @param serviceName имя сервиса
     * @param spans       span-ы для включения в запрос
     * @return построенный запрос
     */
    private ExportTraceServiceRequest buildRequest(final String serviceName, final Span... spans) {
        final Resource resource = Resource.newBuilder()
                .addAttributes(KeyValue.newBuilder()
                        .setKey("service.name")
                        .setValue(AnyValue.newBuilder().setStringValue(serviceName)))
                .build();

        final ScopeSpans scopeSpans =
                ScopeSpans.newBuilder().addAllSpans(List.of(spans)).build();

        final ResourceSpans resourceSpans = ResourceSpans.newBuilder()
                .setResource(resource)
                .addScopeSpans(scopeSpans)
                .build();

        return ExportTraceServiceRequest.newBuilder()
                .addResourceSpans(resourceSpans)
                .build();
    }

    /**
     * Строит HTTP client span с заданными параметрами.
     *
     * @param traceIdHex hex trace ID (32 символа)
     * @param spanIdHex  hex span ID (16 символов)
     * @param name       имя операции
     * @param url        HTTP URL
     * @param method     HTTP метод
     * @return построенный Span
     */
    private Span buildHttpClientSpan(
            final String traceIdHex, final String spanIdHex, final String name, final String url, final String method) {
        final ByteString traceId = ByteString.copyFrom(hexToBytes(traceIdHex));
        final ByteString spanId = ByteString.copyFrom(hexToBytes(spanIdHex));

        return Span.newBuilder()
                .setTraceId(traceId)
                .setSpanId(spanId)
                .setName(name)
                .setStartTimeUnixNano(System.nanoTime())
                .setEndTimeUnixNano(System.nanoTime() + 1_000_000)
                .addAttributes(kv("span.kind", "client"))
                .addAttributes(kv("http.url", url))
                .addAttributes(kv("method", method))
                .addAttributes(kv("status", "200"))
                .addAttributes(kv("client.name", "api.openai.com"))
                .build();
    }

    /**
     * Строит span с указанным span.kind (без HTTP-атрибутов).
     *
     * @param traceIdHex hex trace ID (32 символа)
     * @param spanIdHex  hex span ID (16 символов)
     * @param name       имя операции
     * @param spanKind   тип span (client, server, internal)
     * @return построенный Span
     */
    private Span buildSpanWithKind(
            final String traceIdHex, final String spanIdHex, final String name, final String spanKind) {
        final ByteString traceId = ByteString.copyFrom(hexToBytes(traceIdHex));
        final ByteString spanId = ByteString.copyFrom(hexToBytes(spanIdHex));

        return Span.newBuilder()
                .setTraceId(traceId)
                .setSpanId(spanId)
                .setName(name)
                .setStartTimeUnixNano(System.nanoTime())
                .setEndTimeUnixNano(System.nanoTime() + 1_000_000)
                .addAttributes(kv("span.kind", spanKind))
                .build();
    }

    /**
     * Создаёт OTel KeyValue пару.
     *
     * @param key   ключ атрибута
     * @param value значение атрибута
     * @return построенный KeyValue
     */
    private KeyValue kv(final String key, final String value) {
        return KeyValue.newBuilder()
                .setKey(key)
                .setValue(AnyValue.newBuilder().setStringValue(value))
                .build();
    }

    /**
     * Конвертирует hex строку в массив байтов.
     *
     * @param hex hex строка
     * @return массив байтов
     */
    private byte[] hexToBytes(final String hex) {
        final int len = hex.length();
        final byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
