package io.saira.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.*;

/** Результат shadow-вызова альтернативной модели. */
@Entity
@Table(name = "shadow_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShadowResult {

    /** Уникальный идентификатор записи (auto-generated). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID оригинального span-а (FK к agent_span.span_id). */
    @Column(name = "source_span_id", nullable = false, length = 32)
    private String sourceSpanId;

    /** ID конфигурации shadow-правила (FK к shadow_config.id). Null для ad-hoc тестов. */
    @Column(name = "shadow_config_id")
    private Long shadowConfigId;

    /** Идентификатор модели, использованной для shadow-вызова. */
    @Column(name = "model_id", nullable = false)
    private String modelId;

    /** Тело запроса, отправленного на shadow-модель. */
    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    /** Тело ответа от shadow-модели. */
    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    /** Время выполнения shadow-вызова в миллисекундах. */
    @Column(name = "latency_ms")
    private Long latencyMs;

    /** Количество входных токенов. */
    @Column(name = "input_tokens")
    private Integer inputTokens;

    /** Количество выходных токенов. */
    @Column(name = "output_tokens")
    private Integer outputTokens;

    /** Стоимость вызова в USD. */
    @Column(name = "cost_usd", precision = 12, scale = 6)
    private BigDecimal costUsd;

    /** Статус выполнения (PENDING, SUCCESS, ERROR). */
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "PENDING";

    /** Сообщение об ошибке (если status=ERROR). */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** Время выполнения shadow-вызова. */
    @Column(name = "executed_at")
    private Instant executedAt;

    /** Время создания записи. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
