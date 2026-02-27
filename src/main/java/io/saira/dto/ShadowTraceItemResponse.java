package io.saira.dto;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.*;

/** Агрегированный ответ: один элемент = один трейс с shadow-результатами. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShadowTraceItemResponse {

    /** OpenTelemetry trace ID. */
    private String traceId;

    /** Имя сервиса. */
    private String serviceName;

    /** Количество shadow-результатов для этого трейса. */
    private long shadowCount;

    /** Наивысший балл семантической близости среди результатов. */
    private BigDecimal latestScore;

    /** Идентификатор модели последнего shadow-результата. */
    private String latestModelId;

    /** Время выполнения последнего shadow-вызова. */
    private Instant latestExecutedAt;
}
