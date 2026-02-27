package io.saira.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Конфигурация shadow-провайдеров для мультимодельного тестирования.
 *
 * <p>Example application.yml:
 *
 * <pre>{@code
 * saira:
 *   shadow:
 *     enabled: true
 *     async:
 *       core-pool-size: 4
 *       max-pool-size: 16
 *       queue-capacity: 100
 *     providers:
 *       openrouter:
 *         enabled: true
 *         base-url: https://openrouter.ai/api
 *         api-key: ${OPENROUTER_API_KEY:}
 *         default-temperature: 0.7
 *         default-max-tokens: 4096
 *       gigachat:
 *         enabled: true
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "saira.shadow")
public class ShadowProperties {

    /** Глобальный флаг включения shadow-функциональности. */
    private boolean enabled = true;

    /** Конфигурация async executor для shadow-вызовов. */
    private AsyncConfig async = new AsyncConfig();

    /** Карта провайдеров: ключ = имя провайдера (gigachat, openrouter). */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /** Конфигурация отдельного провайдера AI-модели. */
    @Data
    public static class ProviderConfig {

        /** Включен ли провайдер. */
        private boolean enabled;

        /** Базовый URL API провайдера (для OpenRouter-совместимых). */
        private String baseUrl;

        /** API-ключ для авторизации. */
        private String apiKey;

        /** Температура по умолчанию для запросов к провайдеру. */
        private Double defaultTemperature;

        /** Максимальное количество токенов по умолчанию. */
        private Integer defaultMaxTokens;
    }

    /** Конфигурация пула потоков для асинхронных shadow-вызовов. */
    @Data
    public static class AsyncConfig {

        /** Минимальное количество потоков в пуле. */
        private int corePoolSize = 4;

        /** Максимальное количество потоков в пуле. */
        private int maxPoolSize = 16;

        /** Ёмкость очереди задач. */
        private int queueCapacity = 100;
    }
}
