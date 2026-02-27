package io.saira.service.shadow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.saira.dto.ShadowExecuteRequest;
import io.saira.dto.ShadowResultResponse;
import io.saira.dto.mapper.ShadowMapper;
import io.saira.entity.AgentSpan;
import io.saira.entity.ShadowConfig;
import io.saira.entity.ShadowResult;
import io.saira.repository.AgentSpanRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис выполнения shadow-вызовов к альтернативным AI-моделям.
 *
 * <p>Поддерживает два режима: асинхронный (при перехвате ingestion span-ов)
 * и синхронный (ручной shadow-тест через REST API).
 * Использует ShadowChatClientRegistry для получения ChatClient по modelId.
 */
@Slf4j
@Service
public class ShadowExecutionService {

    /** Статус успешного выполнения. */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /** Статус ошибки выполнения. */
    private static final String STATUS_ERROR = "ERROR";

    /** Статус ожидания выполнения. */
    private static final String STATUS_PENDING = "PENDING";

    /** Количество наносекунд в одной миллисекунде. */
    private static final long NANOS_PER_MILLI = 1_000_000L;

    /** Реестр ChatClient для shadow-вызовов (null если shadow отключён). */
    @Nullable private final ShadowChatClientRegistry shadowChatClientRegistry;

    /** Сервис сохранения shadow-результатов. */
    private final ShadowResultService shadowResultService;

    /** Репозиторий span-ов для поиска по spanId. */
    private final AgentSpanRepository agentSpanRepository;

    /** Пул потоков для асинхронных shadow-вызовов (null если shadow отключён). */
    @Nullable private final AsyncTaskExecutor shadowExecutor;

    /** Jackson ObjectMapper для парсинга request body. */
    private final ObjectMapper objectMapper;

    /** MapStruct маппер для shadow DTO. */
    private final ShadowMapper shadowMapper;

    /** Сервис оценки shadow-результатов (null если метрики недоступны). */
    @Nullable private final ShadowEvaluationService shadowEvaluationService;

    /**
     * Конструктор с опциональными зависимостями от shadow-компонентов.
     *
     * @param shadowChatClientRegistry реестр ChatClient (может быть null)
     * @param shadowResultService      сервис сохранения результатов
     * @param agentSpanRepository      репозиторий span-ов
     * @param shadowExecutor           пул потоков для async вызовов (может быть null)
     * @param objectMapper             Jackson ObjectMapper
     * @param shadowMapper             MapStruct маппер
     * @param shadowEvaluationService  сервис оценки (может быть null)
     */
    public ShadowExecutionService(
            @Autowired(required = false) @Nullable final ShadowChatClientRegistry shadowChatClientRegistry,
            final ShadowResultService shadowResultService,
            final AgentSpanRepository agentSpanRepository,
            @Autowired(required = false) @Qualifier("shadowExecutor") @Nullable final AsyncTaskExecutor shadowExecutor,
            final ObjectMapper objectMapper,
            final ShadowMapper shadowMapper,
            @Autowired(required = false) @Nullable final ShadowEvaluationService shadowEvaluationService) {
        this.shadowChatClientRegistry = shadowChatClientRegistry;
        this.shadowResultService = shadowResultService;
        this.agentSpanRepository = agentSpanRepository;
        this.shadowExecutor = shadowExecutor;
        this.objectMapper = objectMapper;
        this.shadowMapper = shadowMapper;
        this.shadowEvaluationService = shadowEvaluationService;
    }

    /**
     * Выполняет shadow-вызов асинхронно через выделенный пул потоков.
     *
     * @param span   оригинальный span с requestBody
     * @param config shadow-правило с параметрами модели
     * @return CompletableFuture с результатом shadow-вызова
     */
    public CompletableFuture<ShadowResult> executeAsync(final AgentSpan span, final ShadowConfig config) {
        Assert.notNull(span, "span cannot be null");
        Assert.notNull(config, "config cannot be null");

        if (shadowExecutor == null) {
            log.warn("Shadow executor не доступен, пропуск async вызова для span={}", span.getSpanId());
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> executeShadowCall(span, config), shadowExecutor)
                .thenApply(result -> {
                    triggerEvaluation(result, span);
                    return result;
                });
    }

    /**
     * Выполняет ручной shadow-тест синхронно.
     *
     * @param request параметры ручного shadow-теста
     * @return DTO результата shadow-вызова
     */
    public ShadowResultResponse executeManual(final ShadowExecuteRequest request) {
        Assert.notNull(request, "request cannot be null");

        final AgentSpan span = agentSpanRepository
                .findBySpanId(request.getSpanId())
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Span not found: " + request.getSpanId()));

        final ShadowConfig manualConfig = buildManualConfig(request);
        ensureChatClientAvailable(request.getProviderName(), request.getModelId(), request);

        final ShadowResult result = executeShadowCall(span, manualConfig);
        return shadowMapper.toResultResponse(result);
    }

    /**
     * Выполняет shadow-вызов: парсит requestBody, вызывает ChatClient, сохраняет результат.
     *
     * @param span   оригинальный span
     * @param config shadow-конфигурация
     * @return сохранённый ShadowResult
     */
    private ShadowResult executeShadowCall(final AgentSpan span, final ShadowConfig config) {
        final String modelId = config.getModelId();
        log.debug("Выполнение shadow-вызова: span={}, model={}", span.getSpanId(), modelId);

        if (shadowChatClientRegistry == null) {
            log.warn("Shadow реестр не доступен, пропуск вызова для model={}", modelId);
            return saveErrorResult(span, config, "Shadow registry is not available");
        }

        final Optional<ChatClient> clientOpt = shadowChatClientRegistry.getClient(modelId);
        if (clientOpt.isEmpty()) {
            log.warn("ChatClient не найден для модели: {}", modelId);
            return saveErrorResult(span, config, "ChatClient not found for model: " + modelId);
        }

        final String promptContent = extractPromptContent(span.getRequestBody());
        if (promptContent == null || promptContent.isBlank()) {
            return saveErrorResult(span, config, "Could not extract prompt from request body");
        }

        return callChatClientAndSave(clientOpt.get(), promptContent, span, config);
    }

    /**
     * Вызывает ChatClient и сохраняет результат с метриками латентности и токенов.
     *
     * @param chatClient    клиент для вызова модели
     * @param promptContent текст промпта
     * @param span          оригинальный span
     * @param config        shadow-конфигурация
     * @return сохранённый ShadowResult
     */
    private ShadowResult callChatClientAndSave(
            final ChatClient chatClient, final String promptContent, final AgentSpan span, final ShadowConfig config) {
        try {
            final long startNanos = System.nanoTime();
            final ChatResponse chatResponse =
                    chatClient.prompt().user(promptContent).call().chatResponse();
            final long latencyMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLI;

            final String responseContent = extractResponseContent(chatResponse);
            final Integer inputTokens = extractInputTokens(chatResponse);
            final Integer outputTokens = extractOutputTokens(chatResponse);

            final ShadowResult result = ShadowResult.builder()
                    .sourceSpanId(span.getSpanId())
                    .shadowConfigId(config.getId())
                    .modelId(config.getModelId())
                    .requestBody(promptContent)
                    .responseBody(responseContent)
                    .latencyMs(latencyMs)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .status(STATUS_SUCCESS)
                    .executedAt(Instant.now())
                    .build();

            return shadowResultService.save(result);
        } catch (Exception e) {
            log.error(
                    "Ошибка shadow-вызова для span={}, model={}: {}",
                    span.getSpanId(),
                    config.getModelId(),
                    e.getMessage());
            return saveErrorResult(span, config, e.getMessage());
        }
    }

    /**
     * Извлекает текст промпта из JSON request body span-а.
     *
     * <p>Поддерживает форматы:
     * <ul>
     *   <li>OpenAI-совместимый: {@code {"messages": [{"role": "user", "content": "..."}]}}</li>
     *   <li>Простой текст: если JSON не парсится, возвращает как есть</li>
     * </ul>
     *
     * @param requestBody JSON-строка тела запроса
     * @return извлечённый текст промпта или null
     */
    private String extractPromptContent(final String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            return null;
        }

        try {
            final JsonNode root = objectMapper.readTree(requestBody);
            return extractFromOpenAiFormat(root);
        } catch (JsonProcessingException e) {
            log.debug("Request body не является валидным JSON, используем как plain text");
            return requestBody;
        }
    }

    /**
     * Извлекает последнее user-сообщение из OpenAI-совместимого формата.
     *
     * @param root корневой JSON-узел
     * @return текст последнего user-сообщения или весь request как строка
     */
    private String extractFromOpenAiFormat(final JsonNode root) {
        final JsonNode messagesNode = root.get("messages");
        if (messagesNode == null || !messagesNode.isArray() || messagesNode.isEmpty()) {
            return root.toString();
        }

        String lastUserContent = null;
        for (final JsonNode messageNode : messagesNode) {
            final JsonNode roleNode = messageNode.get("role");
            final JsonNode contentNode = messageNode.get("content");
            if (roleNode != null && "user".equals(roleNode.asText()) && contentNode != null) {
                lastUserContent = contentNode.asText();
            }
        }

        if (lastUserContent != null) {
            return lastUserContent;
        }
        return root.toString();
    }

    /**
     * Извлекает текстовый контент из ChatResponse.
     *
     * @param chatResponse ответ ChatClient
     * @return текст ответа или пустая строка
     */
    private String extractResponseContent(final ChatResponse chatResponse) {
        if (chatResponse == null
                || chatResponse.getResults() == null
                || chatResponse.getResults().isEmpty()) {
            return "";
        }
        final Generation generation = chatResponse.getResults().getFirst();
        if (generation.getOutput() == null || generation.getOutput().getText() == null) {
            return "";
        }
        return generation.getOutput().getText();
    }

    /**
     * Извлекает количество входных токенов из метаданных ChatResponse.
     *
     * @param chatResponse ответ ChatClient
     * @return количество входных токенов или null
     */
    private Integer extractInputTokens(final ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        if (chatResponse.getMetadata().getUsage() == null) {
            return null;
        }
        final long tokens = chatResponse.getMetadata().getUsage().getPromptTokens();
        return tokens > 0 ? (int) tokens : null;
    }

    /**
     * Извлекает количество выходных токенов из метаданных ChatResponse.
     *
     * @param chatResponse ответ ChatClient
     * @return количество выходных токенов или null
     */
    private Integer extractOutputTokens(final ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        if (chatResponse.getMetadata().getUsage() == null) {
            return null;
        }
        final long tokens = chatResponse.getMetadata().getUsage().getCompletionTokens();
        return tokens > 0 ? (int) tokens : null;
    }

    /**
     * Сохраняет ShadowResult со статусом ERROR.
     *
     * @param span         оригинальный span
     * @param config       shadow-конфигурация
     * @param errorMessage сообщение об ошибке
     * @return сохранённый ShadowResult с ошибкой
     */
    private ShadowResult saveErrorResult(final AgentSpan span, final ShadowConfig config, final String errorMessage) {
        final ShadowResult result = ShadowResult.builder()
                .sourceSpanId(span.getSpanId())
                .shadowConfigId(config.getId())
                .modelId(config.getModelId())
                .requestBody(span.getRequestBody())
                .status(STATUS_ERROR)
                .errorMessage(errorMessage)
                .executedAt(Instant.now())
                .build();
        return shadowResultService.save(result);
    }

    /**
     * Строит виртуальный ShadowConfig из параметров ручного запроса.
     *
     * @param request параметры ручного shadow-теста
     * @return ShadowConfig для выполнения
     */
    private ShadowConfig buildManualConfig(final ShadowExecuteRequest request) {
        return ShadowConfig.builder()
                .providerName(request.getProviderName())
                .modelId(request.getModelId())
                .samplingRate(BigDecimal.valueOf(100))
                .build();
    }

    /**
     * Обеспечивает доступность ChatClient для указанной модели.
     * Если клиент не зарегистрирован, пытается создать его динамически.
     *
     * @param providerName имя провайдера
     * @param modelId      идентификатор модели
     * @param request      параметры запроса (temperature, maxTokens)
     */
    private void ensureChatClientAvailable(
            final String providerName, final String modelId, final ShadowExecuteRequest request) {
        if (shadowChatClientRegistry == null) {
            return;
        }
        if (shadowChatClientRegistry.hasClient(modelId)) {
            return;
        }

        final Map<String, Object> params = new HashMap<>();
        if (request.getTemperature() != null) {
            params.put("temperature", request.getTemperature().doubleValue());
        }
        if (request.getMaxTokens() != null) {
            params.put("maxTokens", request.getMaxTokens());
        }

        try {
            shadowChatClientRegistry.createClientForModel(providerName, modelId, params);
        } catch (IllegalArgumentException e) {
            log.warn("Не удалось создать ChatClient для модели {}: {}", modelId, e.getMessage());
        }
    }

    /**
     * Запускает оценку shadow-результата, если сервис оценки доступен.
     * Ошибки оценки логируются, но не пробрасываются.
     *
     * @param result shadow-результат для оценки
     * @param span   оригинальный span с эталонным ответом
     */
    private void triggerEvaluation(final ShadowResult result, final AgentSpan span) {
        if (shadowEvaluationService == null) {
            return;
        }
        if (result == null || !STATUS_SUCCESS.equals(result.getStatus())) {
            return;
        }

        try {
            shadowEvaluationService.evaluateResult(result, span);
        } catch (Exception e) {
            log.error("Ошибка при оценке shadow-результата id={}: {}", result.getId(), e.getMessage(), e);
        }
    }
}
