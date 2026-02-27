package io.saira.service.shadow;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;

/**
 * Динамический реестр ChatClient для shadow-вызовов.
 *
 * <p>Хранит готовые ChatClient по modelId и базовые ChatModel / OpenAiApi
 * по providerId для создания новых клиентов через mutate() паттерн.
 * Thread-safe благодаря ConcurrentHashMap.
 *
 * <p>Используется в ShadowAutoConfiguration и ShadowExecutionService.
 */
@Slf4j
public class ShadowChatClientRegistry {

    /** Реестр готовых ChatClient по modelId. */
    private final ConcurrentHashMap<String, ChatClient> clients = new ConcurrentHashMap<>();

    /** Базовые ChatModel по providerId для mutate() паттерна (OpenRouter). */
    private final ConcurrentHashMap<String, ChatModel> baseModels = new ConcurrentHashMap<>();

    /** Базовые OpenAiApi по providerId для mutate() паттерна (OpenRouter). */
    private final ConcurrentHashMap<String, OpenAiApi> baseApis = new ConcurrentHashMap<>();

    /**
     * Регистрирует готовый ChatClient для указанной модели.
     *
     * @param modelId уникальный идентификатор модели
     * @param client  ChatClient для shadow-вызовов
     */
    public void registerClient(final String modelId, final ChatClient client) {
        Assert.hasText(modelId, "modelId cannot be blank");
        Assert.notNull(client, "client cannot be null");
        clients.put(modelId, client);
        log.info("Зарегистрирован ChatClient для модели: {}", modelId);
    }

    /**
     * Возвращает ChatClient для указанной модели.
     *
     * @param modelId уникальный идентификатор модели
     * @return Optional с ChatClient, если зарегистрирован
     */
    public Optional<ChatClient> getClient(final String modelId) {
        Assert.hasText(modelId, "modelId cannot be blank");
        return Optional.ofNullable(clients.get(modelId));
    }

    /**
     * Проверяет наличие ChatClient для указанной модели.
     *
     * @param modelId уникальный идентификатор модели
     * @return true если клиент зарегистрирован
     */
    public boolean hasClient(final String modelId) {
        Assert.hasText(modelId, "modelId cannot be blank");
        return clients.containsKey(modelId);
    }

    /**
     * Удаляет ChatClient для указанной модели.
     *
     * @param modelId уникальный идентификатор модели
     */
    public void unregisterClient(final String modelId) {
        Assert.hasText(modelId, "modelId cannot be blank");
        ChatClient removed = clients.remove(modelId);
        if (removed != null) {
            log.info("Удалён ChatClient для модели: {}", modelId);
        }
    }

    /**
     * Возвращает множество всех зарегистрированных modelId.
     *
     * @return неизменяемое множество идентификаторов моделей
     */
    public Set<String> getAvailableModelIds() {
        return Set.copyOf(clients.keySet());
    }

    /**
     * Регистрирует базовый ChatModel для провайдера (для mutate() паттерна).
     *
     * @param providerId идентификатор провайдера
     * @param chatModel  базовая ChatModel
     */
    public void registerBaseModel(final String providerId, final ChatModel chatModel) {
        Assert.hasText(providerId, "providerId cannot be blank");
        Assert.notNull(chatModel, "chatModel cannot be null");
        baseModels.put(providerId, chatModel);
        log.debug("Зарегистрирована базовая ChatModel для провайдера: {}", providerId);
    }

    /**
     * Регистрирует базовый OpenAiApi для провайдера (для mutate() паттерна).
     *
     * @param providerId идентификатор провайдера
     * @param api        базовый OpenAiApi
     */
    public void registerBaseApi(final String providerId, final OpenAiApi api) {
        Assert.hasText(providerId, "providerId cannot be blank");
        Assert.notNull(api, "api cannot be null");
        baseApis.put(providerId, api);
        log.debug("Зарегистрирован базовый OpenAiApi для провайдера: {}", providerId);
    }

    /**
     * Динамически создаёт ChatClient для конкретной модели через mutate() паттерн.
     *
     * <p>Для OpenRouter-совместимых провайдеров: мутирует базовый OpenAiApi и OpenAiChatModel
     * с указанными параметрами. Для GigaChat: оборачивает базовую ChatModel с defaultOptions.
     *
     * @param providerId идентификатор провайдера
     * @param modelId    идентификатор модели
     * @param params     параметры модели (temperature, maxTokens)
     * @return созданный ChatClient
     * @throws IllegalArgumentException если провайдер не зарегистрирован
     */
    public ChatClient createClientForModel(
            final String providerId, final String modelId, final Map<String, Object> params) {
        Assert.hasText(providerId, "providerId cannot be blank");
        Assert.hasText(modelId, "modelId cannot be blank");

        ChatModel baseChatModel = baseModels.get(providerId);
        Assert.notNull(baseChatModel, "Базовая ChatModel не найдена для провайдера: " + providerId);

        ChatClient chatClient = createClientFromBase(providerId, baseChatModel, modelId, params);
        registerClient(modelId, chatClient);
        return chatClient;
    }

    /**
     * Создаёт ChatClient из базовой модели с заданными параметрами.
     *
     * @param providerId    идентификатор провайдера
     * @param baseChatModel базовая ChatModel
     * @param modelId       идентификатор целевой модели
     * @param params        параметры (temperature, maxTokens)
     * @return готовый ChatClient
     */
    private ChatClient createClientFromBase(
            final String providerId,
            final ChatModel baseChatModel,
            final String modelId,
            final Map<String, Object> params) {

        if (baseChatModel instanceof OpenAiChatModel openAiBase) {
            return createOpenAiCompatibleClient(providerId, openAiBase, modelId, params);
        }
        return createGenericClient(baseChatModel, modelId, params);
    }

    /**
     * Создаёт ChatClient через mutate() паттерн для OpenAI-совместимых провайдеров.
     *
     * @param providerId идентификатор провайдера
     * @param baseModel  базовая OpenAiChatModel
     * @param modelId    целевая модель
     * @param params     параметры
     * @return ChatClient
     */
    private ChatClient createOpenAiCompatibleClient(
            final String providerId,
            final OpenAiChatModel baseModel,
            final String modelId,
            final Map<String, Object> params) {

        OpenAiApi baseApi = baseApis.get(providerId);
        Assert.notNull(baseApi, "Базовый OpenAiApi не найден для провайдера: " + providerId);

        Double temperature = extractParam(params, "temperature", Double.class);
        Integer maxTokens = extractParam(params, "maxTokens", Integer.class);

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(modelId);
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        if (maxTokens != null) {
            optionsBuilder.maxTokens(maxTokens);
        }

        OpenAiChatModel mutatedModel = baseModel
                .mutate()
                .openAiApi(baseApi)
                .defaultOptions(optionsBuilder.build())
                .build();

        log.info("Создан OpenAI-совместимый ChatClient: provider={}, model={}", providerId, modelId);
        return ChatClient.builder(mutatedModel).build();
    }

    /**
     * Создаёт ChatClient для generic провайдеров (GigaChat и др.).
     *
     * @param baseChatModel базовая ChatModel
     * @param modelId       целевая модель
     * @param params        параметры
     * @return ChatClient
     */
    private ChatClient createGenericClient(
            final ChatModel baseChatModel, final String modelId, final Map<String, Object> params) {
        log.info("Создан generic ChatClient для модели: {}", modelId);
        return ChatClient.builder(baseChatModel).build();
    }

    /**
     * Извлекает параметр из Map с приведением типа.
     *
     * @param params   карта параметров
     * @param key      ключ параметра
     * @param type     ожидаемый тип
     * @param <T>      тип параметра
     * @return значение параметра или null
     */
    @SuppressWarnings("unchecked")
    private <T> T extractParam(final Map<String, Object> params, final String key, final Class<T> type) {
        if (params == null) {
            return null;
        }
        Object value = params.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        if (type == Double.class && value instanceof Number number) {
            return (T) Double.valueOf(number.doubleValue());
        }
        if (type == Integer.class && value instanceof Number number) {
            return (T) Integer.valueOf(number.intValue());
        }
        return null;
    }
}
