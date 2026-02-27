package io.saira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Информация о подключённом AI-провайдере для shadow-вызовов. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderInfo {

    /** Имя провайдера (gigachat, openrouter). */
    private String name;

    /** Включён ли провайдер. */
    private boolean enabled;

    /** Базовый URL API провайдера. */
    private String baseUrl;
}
