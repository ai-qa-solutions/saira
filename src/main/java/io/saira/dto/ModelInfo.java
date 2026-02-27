package io.saira.dto;

import lombok.*;

/** Информация о модели от AI-провайдера. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo {

    /** Уникальный идентификатор модели у провайдера. */
    private String id;

    /** Отображаемое имя модели. */
    private String name;

    /** Имя провайдера (gigachat, openrouter). */
    private String provider;

    /** Описание модели. */
    private String description;

    /** Максимальная длина контекста в токенах. */
    private Integer contextLength;
}
