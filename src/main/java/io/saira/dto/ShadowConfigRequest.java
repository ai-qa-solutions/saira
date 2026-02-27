package io.saira.dto;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.*;
import lombok.*;

/** Запрос на создание/обновление shadow-правила. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShadowConfigRequest {

    /** Имя сервиса, для которого применяется shadow-правило. */
    @NotBlank(message = "serviceName is required")
    private String serviceName;

    /** Имя провайдера модели (gigachat, openrouter). */
    @NotBlank(message = "providerName is required")
    private String providerName;

    /** Идентификатор модели у провайдера. */
    @NotBlank(message = "modelId is required")
    private String modelId;

    /** Параметры модели (temperature, maxTokens и т.д.). */
    private Map<String, Object> modelParams;

    /** Процент запросов, направляемых на shadow-модель (0.00-100.00). */
    @NotNull(message = "samplingRate is required") @DecimalMin(value = "0.00", message = "samplingRate must be >= 0")
    @DecimalMax(value = "100.00", message = "samplingRate must be <= 100")
    private BigDecimal samplingRate;
}
