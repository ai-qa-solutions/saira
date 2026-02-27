package io.saira.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.*;

/** Правило shadow-тестирования: сервис -> модель -> параметры -> % запросов. */
@Entity
@Table(name = "shadow_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShadowConfig {

    /** Уникальный идентификатор записи (auto-generated). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Имя сервиса, для которого применяется shadow-правило. */
    @Column(name = "service_name", nullable = false)
    private String serviceName;

    /** Имя провайдера модели (gigachat, openrouter). */
    @Column(name = "provider_name", nullable = false, length = 100)
    private String providerName;

    /** Идентификатор модели у провайдера. */
    @Column(name = "model_id", nullable = false)
    private String modelId;

    /** Параметры модели в JSON-формате (temperature, maxTokens и т.д.). */
    @Column(name = "model_params", columnDefinition = "TEXT")
    private String modelParams;

    /** Процент запросов, направляемых на shadow-модель (0.00-100.00). */
    @Column(name = "sampling_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal samplingRate = new BigDecimal("100.00");

    /** Статус правила (ACTIVE, DISABLED). */
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "ACTIVE";

    /** Время создания записи. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Время последнего обновления записи. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        final Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
