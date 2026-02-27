package io.saira.service.shadow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import chat.giga.springai.api.chat.GigaChatApi;
import chat.giga.springai.api.chat.models.ModelsResponse;
import io.saira.config.ShadowProperties;
import io.saira.config.ShadowProperties.ProviderConfig;
import io.saira.dto.ModelInfo;
import io.saira.dto.ProviderInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис обнаружения моделей от AI-провайдеров (GigaChat, OpenRouter).
 *
 * <p>Вызывает API провайдеров для получения списка доступных моделей.
 * Результаты кэшируются через Caffeine с 5-минутным TTL.
 */
@Slf4j
@Service
public class ModelDiscoveryService {

    /** Имя провайдера OpenRouter. */
    private static final String PROVIDER_OPENROUTER = "openrouter";

    /** Имя провайдера GigaChat. */
    private static final String PROVIDER_GIGACHAT = "gigachat";

    /** Конфигурация shadow-провайдеров. */
    private final ShadowProperties shadowProperties;

    /** HTTP-клиент для запросов к API провайдеров. */
    private final RestClient restClient;

    /** GigaChat API клиент (может быть null если GigaChat не настроен). */
    @Nullable private final GigaChatApi gigaChatApi;

    /**
     * Создаёт сервис обнаружения моделей.
     *
     * @param shadowProperties конфигурация shadow-провайдеров
     * @param gigaChatApi      GigaChat API клиент (опциональный)
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ModelDiscoveryService(
            final ShadowProperties shadowProperties,
            @org.springframework.beans.factory.annotation.Autowired(required = false) @Nullable final GigaChatApi gigaChatApi) {
        this.shadowProperties = shadowProperties;
        this.restClient = RestClient.create();
        this.gigaChatApi = gigaChatApi;
    }

    /**
     * Создаёт сервис обнаружения моделей (для тестирования).
     *
     * @param shadowProperties конфигурация shadow-провайдеров
     * @param restClient       HTTP-клиент
     * @param gigaChatApi      GigaChat API клиент (может быть null)
     */
    ModelDiscoveryService(
            final ShadowProperties shadowProperties,
            final RestClient restClient,
            @Nullable final GigaChatApi gigaChatApi) {
        this.shadowProperties = shadowProperties;
        this.restClient = restClient;
        this.gigaChatApi = gigaChatApi;
    }

    /**
     * Получает список моделей от указанного провайдера.
     *
     * <p>Результаты кэшируются с ключом providerName.
     * При ошибке HTTP-запроса логирует предупреждение и возвращает пустой список.
     *
     * @param providerName имя провайдера (gigachat, openrouter)
     * @return список доступных моделей или пустой список при ошибке
     */
    @Cacheable(value = "shadow-models", key = "#providerName")
    public List<ModelInfo> listModels(final String providerName) {
        log.debug("Запрос списка моделей от провайдера: {}", providerName);

        ProviderConfig config = getProviderConfig(providerName);
        if (config == null || !config.isEnabled()) {
            log.warn("Провайдер не найден или отключен: {}", providerName);
            return Collections.emptyList();
        }

        return switch (providerName.toLowerCase()) {
            case PROVIDER_OPENROUTER -> fetchOpenRouterModels(config);
            case PROVIDER_GIGACHAT -> fetchGigaChatModels();
            default -> {
                log.warn("Неизвестный провайдер: {}", providerName);
                yield Collections.emptyList();
            }
        };
    }

    /**
     * Возвращает список подключённых провайдеров (enabled=true).
     *
     * @return список информации о подключённых провайдерах
     */
    public List<ProviderInfo> getConnectedProviders() {
        Map<String, ProviderConfig> providers = shadowProperties.getProviders();
        if (providers == null || providers.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProviderInfo> result = new ArrayList<>();
        providers.forEach((name, config) -> {
            if (config.isEnabled()) {
                result.add(ProviderInfo.builder()
                        .name(name)
                        .enabled(true)
                        .baseUrl(config.getBaseUrl())
                        .build());
            }
        });
        return result;
    }

    /**
     * Возвращает список имён подключённых провайдеров.
     *
     * @return список имён провайдеров с enabled=true
     */
    public List<String> getConnectedProviderNames() {
        Map<String, ProviderConfig> providers = shadowProperties.getProviders();
        if (providers == null || providers.isEmpty()) {
            return Collections.emptyList();
        }

        return providers.entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .toList();
    }

    /**
     * Проверяет, подключён ли указанный провайдер (существует и enabled=true).
     *
     * @param providerName имя провайдера
     * @return true если провайдер подключён
     */
    public boolean isProviderConnected(final String providerName) {
        ProviderConfig config = getProviderConfig(providerName);
        return config != null && config.isEnabled();
    }

    /**
     * Получает конфигурацию провайдера из ShadowProperties.
     *
     * @param providerName имя провайдера
     * @return конфигурация провайдера или null
     */
    private ProviderConfig getProviderConfig(final String providerName) {
        Map<String, ProviderConfig> providers = shadowProperties.getProviders();
        if (providers == null) {
            return null;
        }
        return providers.get(providerName.toLowerCase());
    }

    /**
     * Запрашивает список моделей у OpenRouter API.
     *
     * @param config конфигурация провайдера OpenRouter
     * @return список моделей или пустой список при ошибке
     */
    private List<ModelInfo> fetchOpenRouterModels(final ProviderConfig config) {
        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn("OpenRouter: base-url или api-key не заданы");
            return Collections.emptyList();
        }

        try {
            OpenRouterModelsResponse response = restClient
                    .get()
                    .uri(baseUrl + "/v1/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(OpenRouterModelsResponse.class);

            return mapOpenRouterModels(response);
        } catch (RestClientException e) {
            log.warn("Ошибка при запросе моделей OpenRouter: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Запрашивает список моделей через GigaChatApi (spring-ai-gigachat).
     * GigaChatApi самостоятельно обрабатывает OAuth2 и SSL.
     *
     * @return список моделей или пустой список при ошибке
     */
    private List<ModelInfo> fetchGigaChatModels() {
        if (gigaChatApi == null) {
            log.warn("GigaChat: GigaChatApi бин не доступен, проверьте spring.ai.gigachat конфигурацию");
            return Collections.emptyList();
        }

        try {
            ResponseEntity<ModelsResponse> responseEntity = gigaChatApi.models();
            ModelsResponse response = responseEntity.getBody();
            if (response == null || response.getData() == null) {
                return Collections.emptyList();
            }

            return response.getData().stream()
                    .map(model -> ModelInfo.builder()
                            .id(model.getId())
                            .name(model.getId())
                            .provider(PROVIDER_GIGACHAT)
                            .description(model.getType())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.warn("Ошибка при запросе моделей GigaChat: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Маппит ответ OpenRouter API в список ModelInfo.
     *
     * @param response ответ OpenRouter API
     * @return список ModelInfo
     */
    private List<ModelInfo> mapOpenRouterModels(final OpenRouterModelsResponse response) {
        if (response == null || response.getData() == null) {
            return Collections.emptyList();
        }

        return response.getData().stream()
                .map(model -> ModelInfo.builder()
                        .id(model.getId())
                        .name(model.getName())
                        .provider(PROVIDER_OPENROUTER)
                        .description(model.getDescription())
                        .contextLength(model.getContextLength())
                        .build())
                .toList();
    }

    /** Ответ OpenRouter API: список моделей. */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class OpenRouterModelsResponse {

        /** Список моделей. */
        private List<OpenRouterModel> data;

        /** Информация о модели от OpenRouter. */
        @lombok.Data
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        static class OpenRouterModel {

            /** Идентификатор модели (например, "anthropic/claude-3.5-sonnet"). */
            private String id;

            /** Отображаемое имя модели. */
            private String name;

            /** Описание модели. */
            private String description;

            /** Максимальная длина контекста в токенах. */
            private Integer contextLength;

            /** Информация о ценообразовании. */
            private Pricing pricing;

            /** Ценообразование модели OpenRouter. */
            @lombok.Data
            @lombok.NoArgsConstructor
            @lombok.AllArgsConstructor
            static class Pricing {

                /** Стоимость за промпт-токен. */
                private String prompt;

                /** Стоимость за completion-токен. */
                private String completion;
            }
        }
    }
}
