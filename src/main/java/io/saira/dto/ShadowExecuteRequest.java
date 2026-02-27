package io.saira.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** Запрос на ручной shadow-тест для конкретного span-а. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShadowExecuteRequest {

    /** ID span-а для shadow-тестирования. */
    @NotBlank(message = "spanId is required")
    private String spanId;

    /** Идентификатор модели для shadow-вызова. */
    @NotBlank(message = "modelId is required")
    private String modelId;

    /** Имя провайдера модели. */
    @NotBlank(message = "providerName is required")
    private String providerName;

    /** Температура генерации (0.0-2.0). */
    private BigDecimal temperature;

    /** Максимальное количество токенов в ответе. */
    private Integer maxTokens;
}
