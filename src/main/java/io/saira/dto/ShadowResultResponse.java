package io.saira.dto;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.*;

/** Ответ с данными shadow-результата и оценки. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShadowResultResponse {

    /** Уникальный идентификатор результата. */
    private Long id;

    /** ID оригинального span-а. */
    private String sourceSpanId;

    /** ID shadow-конфигурации. */
    private Long shadowConfigId;

    /** Идентификатор модели, использованной для shadow-вызова. */
    private String modelId;

    /** Тело запроса, отправленного на shadow-модель. */
    private String requestBody;

    /** Тело ответа от shadow-модели. */
    private String responseBody;

    /** Время выполнения shadow-вызова в миллисекундах. */
    private Long latencyMs;

    /** Количество входных токенов. */
    private Integer inputTokens;

    /** Количество выходных токенов. */
    private Integer outputTokens;

    /** Стоимость вызова в USD. */
    private BigDecimal costUsd;

    /** Статус выполнения (PENDING, SUCCESS, ERROR). */
    private String status;

    /** Сообщение об ошибке (если status=ERROR). */
    private String errorMessage;

    /** Время выполнения shadow-вызова. */
    private Instant executedAt;

    /** Время создания записи. */
    private Instant createdAt;

    /** Семантическая близость к оригиналу (из evaluation). */
    private BigDecimal semanticSimilarity;

    /** Оценка корректности (из evaluation). */
    private BigDecimal correctnessScore;
}
