package io.saira.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.*;

/** HTTP client span агента — структурированные поля для shadow-вызовов. */
@Entity
@Table(name = "agent_span")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentSpan {

    /** Уникальный идентификатор записи (auto-generated). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** OpenTelemetry trace ID (FK к agent_trace). */
    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    /** OpenTelemetry span ID. */
    @Column(name = "span_id", nullable = false, unique = true, length = 32)
    private String spanId;

    /** ID родительского span. */
    @Column(name = "parent_span_id", length = 32)
    private String parentSpanId;

    /** Имя операции (из Span.name). */
    @Column(name = "operation_name", nullable = false, length = 500)
    private String operationName;

    /** Тип span (CLIENT, SERVER, INTERNAL). */
    @Column(name = "span_kind", nullable = false, length = 32)
    @Builder.Default
    private String spanKind = "CLIENT";

    /** Время начала span. */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** Длительность span в микросекундах. */
    @Column(name = "duration_micros", nullable = false)
    private Long durationMicros;

    /** Код статуса span (OK, ERROR, UNSET). */
    @Column(name = "status_code", length = 32)
    private String statusCode;

    // --- Structured HTTP fields (extracted from span attributes) ---

    /** URL вызова (из атрибута http.url). */
    @Column(name = "http_url", length = 2000)
    private String httpUrl;

    /** HTTP метод (из атрибута method). */
    @Column(name = "http_method", length = 16)
    private String httpMethod;

    /** HTTP статус ответа (из атрибута status). */
    @Column(name = "http_status", length = 16)
    private String httpStatus;

    /** Имя клиента / hostname (из атрибута client.name). */
    @Column(name = "client_name", length = 500)
    private String clientName;

    /** Тело HTTP запроса (из атрибута http.request.body). */
    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    /** Тело HTTP ответа (из атрибута http.response.body). */
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    /** Результат вызова — SUCCESS/FAILURE (из атрибута outcome). */
    @Column(name = "outcome", length = 32)
    private String outcome;
}
