package io.saira.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;
import lombok.*;

/** Оценка качества shadow-результата через spring-ai-ragas метрики. */
@Entity
@Table(name = "shadow_evaluation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShadowEvaluation {

    /** Уникальный идентификатор записи (auto-generated). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID shadow-результата (FK к shadow_result.id). */
    @Column(name = "shadow_result_id", nullable = false)
    private Long shadowResultId;

    /** Семантическая близость shadow-ответа к оригиналу (0.0000-1.0000). */
    @Column(name = "semantic_similarity", precision = 5, scale = 4)
    private BigDecimal semanticSimilarity;

    /** Оценка корректности shadow-ответа (0.0000-1.0000). */
    @Column(name = "correctness_score", precision = 5, scale = 4)
    private BigDecimal correctnessScore;

    /** Детали оценки в JSON-формате (метрики, пояснения). */
    @Column(name = "evaluation_detail", columnDefinition = "TEXT")
    private String evaluationDetail;

    /** Время проведения оценки. */
    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

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
