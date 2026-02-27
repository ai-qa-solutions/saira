package io.saira.service.shadow;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * Использует ShadowModelRegistry для получения ChatModel по провайдеру.
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

    /** Реестр ChatModel для shadow-вызовов (null если shadow отключён). */
    @Nullable private final ShadowModelRegistry shadowModelRegistry;

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
     * @param shadowModelRegistry     реестр ChatModel (может быть null)
     * @param shadowResultService     сервис сохранения результатов
     * @param agentSpanRepository     репозиторий span-ов
     * @param shadowExecutor          пул потоков для async вызовов (может быть null)
     * @param objectMapper            Jackson ObjectMapper
     * @param shadowMapper            MapStruct маппер
     * @param shadowEvaluationService сервис оценки (может быть null)
     */
    public ShadowExecutionService(
            @Autowired(required = false) @Nullable final ShadowModelRegistry shadowModelRegistry,
            final ShadowResultService shadowResultService,
            final AgentSpanRepository agentSpanRepository,
            @Autowired(required = false) @Qualifier("shadowExecutor") @Nullable final AsyncTaskExecutor shadowExecutor,
            final ObjectMapper objectMapper,
            final ShadowMapper shadowMapper,
            @Autowired(required = false) @Nullable final ShadowEvaluationService shadowEvaluationService) {
        this.shadowModelRegistry = shadowModelRegistry;
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

        if (shadowModelRegistry == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Shadow registry not available");
        }

        final ChatModel chatModel = shadowModelRegistry
                .getModel(request.getProviderName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Provider not registered: " + request.getProviderName()));

        final List<Message> messages = extractMessages(span.getRequestBody());
        if (messages.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Could not extract messages from span request body");
        }

        final ShadowConfig manualConfig = buildManualConfig(request);
        final ChatOptions options = buildManualChatOptions(request);

        final ShadowResult result = callChatModelAndSave(chatModel, messages, options, span, manualConfig);
        return shadowMapper.toResultResponse(result);
    }

    /**
     * Выполняет shadow-вызов: парсит requestBody, вызывает ChatModel, сохраняет результат.
     *
     * @param span   оригинальный span
     * @param config shadow-конфигурация
     * @return сохранённый ShadowResult
     */
    private ShadowResult executeShadowCall(final AgentSpan span, final ShadowConfig config) {
        final String modelId = config.getModelId();
        log.debug("Выполнение shadow-вызова: span={}, model={}", span.getSpanId(), modelId);

        if (shadowModelRegistry == null) {
            log.warn("Shadow реестр не доступен, пропуск вызова для model={}", modelId);
            return saveErrorResult(span, config, "Shadow registry is not available");
        }

        final Optional<ChatModel> modelOpt = shadowModelRegistry.getModel(config.getProviderName());
        if (modelOpt.isEmpty()) {
            log.warn("ChatModel не найден для провайдера: {}", config.getProviderName());
            return saveErrorResult(span, config, "Provider not registered: " + config.getProviderName());
        }

        final List<Message> messages = extractMessages(span.getRequestBody());
        if (messages.isEmpty()) {
            return saveErrorResult(span, config, "Could not extract messages from request body");
        }

        final ChatOptions options = buildChatOptions(config);
        return callChatModelAndSave(modelOpt.get(), messages, options, span, config);
    }

    /**
     * Строит ChatOptions из параметров ShadowConfig.
     *
     * @param config shadow-конфигурация с modelParams JSON
     * @return ChatOptions для вызова модели
     */
    private ChatOptions buildChatOptions(final ShadowConfig config) {
        ChatOptions.Builder builder = ChatOptions.builder().model(config.getModelId());

        Map<String, Object> params = parseModelParams(config.getModelParams());
        Double temperature = extractParam(params, "temperature", Double.class);
        Integer maxTokens = extractParam(params, "maxTokens", Integer.class);

        if (temperature != null) {
            builder.temperature(temperature);
        }
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        return builder.build();
    }

    /**
     * Строит ChatOptions из параметров ручного запроса.
     *
     * @param request параметры ручного shadow-теста
     * @return ChatOptions для вызова модели
     */
    private ChatOptions buildManualChatOptions(final ShadowExecuteRequest request) {
        ChatOptions.Builder builder = ChatOptions.builder().model(request.getModelId());
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature().doubleValue());
        }
        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens());
        }
        return builder.build();
    }

    /**
     * Парсит JSON-строку параметров модели в Map.
     *
     * @param json JSON-строка с параметрами модели
     * @return Map с параметрами или пустая Map при ошибке
     */
    private Map<String, Object> parseModelParams(final String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Невалидный JSON modelParams: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Извлекает типизированный параметр из Map с безопасным приведением типов.
     *
     * @param params Map с параметрами
     * @param key    ключ параметра
     * @param type   ожидаемый тип
     * @param <T>    тип значения
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

    /**
     * Извлекает список сообщений из JSON request body span-а.
     *
     * <p>Поддерживает форматы:
     * <ul>
     *   <li>OpenAI-совместимый: {@code {"messages": [{"role": "user", "content": "..."}]}}</li>
     *   <li>Простой текст: если JSON не парсится, оборачивает в UserMessage</li>
     * </ul>
     *
     * @param requestBody JSON-строка тела запроса
     * @return список Message для Prompt или пустой список
     */
    private List<Message> extractMessages(final String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(requestBody);
            JsonNode messagesNode = root.get("messages");
            if (messagesNode == null || !messagesNode.isArray() || messagesNode.isEmpty()) {
                return List.of(new UserMessage(requestBody));
            }
            List<Message> messages = new ArrayList<>();
            for (JsonNode msgNode : messagesNode) {
                String role = msgNode.has("role") ? msgNode.get("role").asText() : "user";
                String content = msgNode.has("content") ? msgNode.get("content").asText() : "";
                switch (role) {
                    case "system" -> messages.add(new SystemMessage(content));
                    case "assistant" -> messages.add(new AssistantMessage(content));
                    default -> messages.add(new UserMessage(content));
                }
            }
            return messages.isEmpty() ? List.of(new UserMessage(requestBody)) : messages;
        } catch (JsonProcessingException e) {
            return List.of(new UserMessage(requestBody));
        }
    }

    /**
     * Вызывает ChatModel и сохраняет результат с метриками латентности и токенов.
     *
     * @param chatModel ChatModel для вызова
     * @param messages  список сообщений для Prompt
     * @param options   параметры вызова (model, temperature, maxTokens)
     * @param span      оригинальный span
     * @param config    shadow-конфигурация
     * @return сохранённый ShadowResult
     */
    private ShadowResult callChatModelAndSave(
            final ChatModel chatModel,
            final List<Message> messages,
            final ChatOptions options,
            final AgentSpan span,
            final ShadowConfig config) {
        try {
            final long startNanos = System.nanoTime();
            final ChatResponse chatResponse = chatModel.call(new Prompt(messages, options));
            final long latencyMs = (System.nanoTime() - startNanos) / NANOS_PER_MILLI;

            final String responseContent = extractResponseContent(chatResponse);
            final Integer inputTokens = extractInputTokens(chatResponse);
            final Integer outputTokens = extractOutputTokens(chatResponse);

            final ShadowResult result = ShadowResult.builder()
                    .sourceSpanId(span.getSpanId())
                    .shadowConfigId(config.getId())
                    .modelId(config.getModelId())
                    .requestBody(span.getRequestBody())
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
                    "Ошибка shadow-вызова: span={}, model={}: {}",
                    span.getSpanId(),
                    config.getModelId(),
                    e.getMessage());
            return saveErrorResult(span, config, e.getMessage());
        }
    }

    /**
     * Извлекает текстовый контент из ChatResponse.
     *
     * @param chatResponse ответ ChatModel
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
     * @param chatResponse ответ ChatModel
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
     * @param chatResponse ответ ChatModel
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
