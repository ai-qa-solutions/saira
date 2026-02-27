package io.saira.service.shadow;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;

/**
 * Реестр ChatModel по провайдерам для shadow-вызовов.
 *
 * <p>Хранит по одной ChatModel на каждый провайдер (например, "openrouter", "gigachat").
 * Thread-safe благодаря ConcurrentHashMap.
 *
 * <p>Бин создаётся в {@link io.saira.config.ShadowAutoConfiguration}.
 */
@Slf4j
public class ShadowModelRegistry {

    /** Реестр ChatModel по providerId. */
    private final ConcurrentHashMap<String, ChatModel> models = new ConcurrentHashMap<>();

    /**
     * Регистрирует ChatModel для указанного провайдера.
     *
     * @param providerId идентификатор провайдера (например, "openrouter", "gigachat")
     * @param model      ChatModel для shadow-вызовов
     */
    public void registerModel(final String providerId, final ChatModel model) {
        Assert.hasText(providerId, "providerId cannot be blank");
        Assert.notNull(model, "model cannot be null");
        models.put(providerId, model);
        log.info("Зарегистрирована ChatModel для провайдера: {}", providerId);
    }

    /**
     * Возвращает ChatModel для указанного провайдера.
     *
     * @param providerId идентификатор провайдера
     * @return Optional с ChatModel, если зарегистрирована
     */
    public Optional<ChatModel> getModel(final String providerId) {
        Assert.hasText(providerId, "providerId cannot be blank");
        return Optional.ofNullable(models.get(providerId));
    }

    /**
     * Проверяет наличие ChatModel для указанного провайдера.
     *
     * @param providerId идентификатор провайдера
     * @return true если модель зарегистрирована
     */
    public boolean hasProvider(final String providerId) {
        Assert.hasText(providerId, "providerId cannot be blank");
        return models.containsKey(providerId);
    }

    /**
     * Возвращает множество всех зарегистрированных провайдеров.
     *
     * @return неизменяемое множество идентификаторов провайдеров
     */
    public Set<String> getAvailableProviders() {
        return Set.copyOf(models.keySet());
    }
}
