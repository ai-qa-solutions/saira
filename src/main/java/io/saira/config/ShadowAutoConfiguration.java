package io.saira.config;

import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import io.saira.service.shadow.ShadowModelRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Автоконфигурация shadow-провайдеров.
 *
 * <p>Создаёт ShadowModelRegistry и AsyncTaskExecutor для shadow-вызовов.
 * Обнаруживает GigaChat ChatModel бины из Spring контекста и регистрирует
 * OpenRouter-совместимые провайдеры.
 *
 * <p>Активируется при saira.shadow.enabled=true (по умолчанию включена).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(ShadowProperties.class)
@ConditionalOnProperty(prefix = "saira.shadow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ShadowAutoConfiguration {

    /** Имя провайдера OpenRouter. */
    private static final String PROVIDER_OPENROUTER = "openrouter";

    /** Имя провайдера GigaChat. */
    private static final String PROVIDER_GIGACHAT = "gigachat";

    /**
     * Создаёт реестр моделей для shadow-вызовов.
     *
     * <p>При старте регистрирует базовые модели для каждого включённого провайдера:
     * OpenRouter через OpenAiChatModel, GigaChat через auto-detection бинов.
     *
     * @param properties конфигурация shadow-провайдеров
     * @param chatModels все ChatModel бины из Spring контекста (может быть пустой)
     * @return настроенный ShadowModelRegistry
     */
    @Bean
    public ShadowModelRegistry shadowModelRegistry(
            final ShadowProperties properties, @Autowired(required = false) final Map<String, ChatModel> chatModels) {

        ShadowModelRegistry registry = new ShadowModelRegistry();

        Map<String, ShadowProperties.ProviderConfig> providers = properties.getProviders();
        if (providers == null || providers.isEmpty()) {
            log.info("Shadow провайдеры не настроены");
            return registry;
        }

        registerOpenRouterProvider(registry, providers);
        registerGigaChatProvider(registry, providers, chatModels);

        log.info("ShadowModelRegistry создан, доступные провайдеры: {}", registry.getAvailableProviders());
        return registry;
    }

    /**
     * Создаёт выделенный пул потоков для асинхронных shadow-вызовов.
     *
     * @param properties конфигурация shadow-провайдеров
     * @return настроенный AsyncTaskExecutor
     */
    @Bean(name = "shadowExecutor")
    public AsyncTaskExecutor shadowExecutor(final ShadowProperties properties) {
        ShadowProperties.AsyncConfig asyncConfig = properties.getAsync();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(asyncConfig.getCorePoolSize());
        executor.setMaxPoolSize(asyncConfig.getMaxPoolSize());
        executor.setQueueCapacity(asyncConfig.getQueueCapacity());
        executor.setThreadNamePrefix("shadow-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info(
                "Shadow executor создан: core={}, max={}, queue={}",
                asyncConfig.getCorePoolSize(),
                asyncConfig.getMaxPoolSize(),
                asyncConfig.getQueueCapacity());
        return executor;
    }

    /**
     * Регистрирует OpenRouter-совместимый провайдер.
     *
     * @param registry  реестр моделей
     * @param providers карта конфигураций провайдеров
     */
    private void registerOpenRouterProvider(
            final ShadowModelRegistry registry, final Map<String, ShadowProperties.ProviderConfig> providers) {

        ShadowProperties.ProviderConfig config = providers.get(PROVIDER_OPENROUTER);
        if (config == null || !config.isEnabled()) {
            log.debug("OpenRouter провайдер отключен или не настроен");
            return;
        }

        String baseUrl = config.getBaseUrl();
        String apiKey = config.getApiKey();
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn("OpenRouter провайдер включен, но base-url или api-key не заданы — пропускаем");
            return;
        }

        OpenAiApi openRouterApi =
                OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();
        if (config.getDefaultTemperature() != null) {
            optionsBuilder.temperature(config.getDefaultTemperature());
        }
        if (config.getDefaultMaxTokens() != null) {
            optionsBuilder.maxTokens(config.getDefaultMaxTokens());
        }

        OpenAiChatModel openRouterModel = OpenAiChatModel.builder()
                .openAiApi(openRouterApi)
                .defaultOptions(optionsBuilder.build())
                .build();

        registry.registerModel(PROVIDER_OPENROUTER, openRouterModel);
        log.info("OpenRouter провайдер зарегистрирован: baseUrl={}", baseUrl);
    }

    /**
     * Обнаруживает GigaChat ChatModel бин из Spring контекста и регистрирует его.
     *
     * @param registry   реестр моделей
     * @param providers  карта конфигураций провайдеров
     * @param chatModels все ChatModel бины из Spring контекста
     */
    private void registerGigaChatProvider(
            final ShadowModelRegistry registry,
            final Map<String, ShadowProperties.ProviderConfig> providers,
            final Map<String, ChatModel> chatModels) {

        ShadowProperties.ProviderConfig config = providers.get(PROVIDER_GIGACHAT);
        if (config == null || !config.isEnabled()) {
            log.debug("GigaChat провайдер отключен или не настроен");
            return;
        }

        if (chatModels == null || chatModels.isEmpty()) {
            log.warn("GigaChat провайдер включен, но ChatModel бины не обнаружены в контексте");
            return;
        }

        chatModels.forEach((beanName, chatModel) -> {
            if (isGigaChatModel(chatModel)) {
                registry.registerModel(PROVIDER_GIGACHAT, chatModel);
                log.info("GigaChat ChatModel обнаружен: bean={}", beanName);
            }
        });
    }

    /**
     * Проверяет, является ли ChatModel экземпляром GigaChat (не OpenAI).
     *
     * @param chatModel ChatModel для проверки
     * @return true если это GigaChat модель
     */
    private boolean isGigaChatModel(final ChatModel chatModel) {
        if (chatModel instanceof OpenAiChatModel) {
            return false;
        }
        String className = chatModel.getClass().getName().toLowerCase();
        return className.contains("gigachat");
    }
}
