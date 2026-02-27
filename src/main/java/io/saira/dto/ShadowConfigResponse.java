package io.saira.dto;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.*;

/** Ответ с данными shadow-правила. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShadowConfigResponse {

    /** Уникальный идентификатор конфигурации. */
    private Long id;

    /** Имя сервиса, для которого применяется shadow-правило. */
    private String serviceName;

    /** Имя провайдера модели (gigachat, openrouter). */
    private String providerName;

    /** Идентификатор модели у провайдера. */
    private String modelId;

    /** Параметры модели в JSON-формате. */
    private String modelParams;

    /** Процент запросов, направляемых на shadow-модель. */
    private BigDecimal samplingRate;

    /** Статус правила (ACTIVE, DISABLED). */
    private String status;

    /** Время создания записи. */
    private Instant createdAt;

    /** Время последнего обновления записи. */
    private Instant updatedAt;
}
