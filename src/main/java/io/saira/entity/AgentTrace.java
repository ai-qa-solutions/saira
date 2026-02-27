package io.saira.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.*;

/** Трейс агента — одна запись на уникальный trace_id. */
@Entity
@Table(name = "agent_trace")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentTrace {

    /** Уникальный идентификатор записи (auto-generated). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** OpenTelemetry trace ID. */
    @Column(name = "trace_id", nullable = false, unique = true, length = 64)
    private String traceId;

    /** Имя сервиса из OTel Resource. */
    @Column(name = "service_name", nullable = false)
    private String serviceName;

    /** Идентификатор FP (из атрибута cm.otel.fp.id). */
    @Column(name = "fp_id")
    private String fpId;

    /** Идентификатор модуля FP (из атрибута cm.otel.fp.module.id). */
    @Column(name = "fp_module_id")
    private String fpModuleId;

    /** Время начала первого span в трейсе. */
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    /** Время окончания последнего span в трейсе. */
    @Column(name = "ended_at")
    private Instant endedAt;

    /** Количество HTTP client span-ов в трейсе. */
    @Column(name = "span_count", nullable = false)
    @Builder.Default
    private Integer spanCount = 0;

    /** Статус обработки трейса (RECEIVED, PROCESSED, ERROR). */
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private String status = "RECEIVED";

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
