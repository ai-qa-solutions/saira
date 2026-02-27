package io.saira.service.shadow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.saira.dto.ShadowExecuteRequest;
import io.saira.dto.ShadowResultResponse;
import io.saira.dto.mapper.ShadowMapper;
import io.saira.entity.AgentSpan;
import io.saira.entity.ShadowConfig;
import io.saira.entity.ShadowResult;
import io.saira.repository.AgentSpanRepository;

/** Unit-тесты сервиса выполнения shadow-вызовов. */
@ExtendWith(MockitoExtension.class)
class ShadowExecutionServiceTest {

    @Mock
    private ShadowChatClientRegistry shadowChatClientRegistry;

    @Mock
    private ShadowResultService shadowResultService;

    @Mock
    private AgentSpanRepository agentSpanRepository;

    @Mock
    private AsyncTaskExecutor shadowExecutor;

    @Mock
    private ShadowMapper shadowMapper;

    @Mock
    private ShadowEvaluationService shadowEvaluationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("executeAsync()")
    class ExecuteAsync {

        @Test
        @DisplayName("should return completed null when shadowExecutor is null")
        void should_ReturnCompletedNull_When_ExecutorNull() {
            // given
            final ShadowExecutionService service = new ShadowExecutionService(
                    shadowChatClientRegistry,
                    shadowResultService,
                    agentSpanRepository,
                    null,
                    objectMapper,
                    shadowMapper,
                    shadowEvaluationService);

            final AgentSpan span = buildTestSpan("span-001", "trace-001");
            final ShadowConfig config = buildTestConfig(1L, "gpt-4");

            // when
            final CompletableFuture<ShadowResult> future = service.executeAsync(span, config);

            // then
            assertThat(future).isCompletedWithValue(null);
        }

        @Test
        @DisplayName("should save error result when registry is null via synchronous executor")
        void should_SaveErrorResult_When_RegistryNull() {
            // given — use a synchronous executor to run the async task inline
            final AsyncTaskExecutor syncExecutor = mock(AsyncTaskExecutor.class);
            doAnswer(invocation -> {
                        ((Runnable) invocation.getArgument(0)).run();
                        return null;
                    })
                    .when(syncExecutor)
                    .execute(any(Runnable.class));

            final ShadowExecutionService service = new ShadowExecutionService(
                    null, shadowResultService, agentSpanRepository, syncExecutor, objectMapper, shadowMapper, null);

            final AgentSpan span = buildTestSpan("span-001", "trace-001");
            final ShadowConfig config = buildTestConfig(1L, "gpt-4");

            final ShadowResult errorResult = ShadowResult.builder()
                    .id(1L)
                    .status("ERROR")
                    .errorMessage("Shadow registry is not available")
                    .build();
            when(shadowResultService.save(any(ShadowResult.class))).thenReturn(errorResult);

            // when
            final CompletableFuture<ShadowResult> future = service.executeAsync(span, config);
            future.join();

            // then
            verify(shadowResultService).save(any(ShadowResult.class));
        }

        @Test
        @DisplayName("should save error result when ChatClient not found for model")
        void should_SaveErrorResult_When_ChatClientNotFound() {
            // given — use a synchronous executor to run the async task inline
            final AsyncTaskExecutor syncExecutor = mock(AsyncTaskExecutor.class);
            doAnswer(invocation -> {
                        ((Runnable) invocation.getArgument(0)).run();
                        return null;
                    })
                    .when(syncExecutor)
                    .execute(any(Runnable.class));

            final ShadowExecutionService service = new ShadowExecutionService(
                    shadowChatClientRegistry,
                    shadowResultService,
                    agentSpanRepository,
                    syncExecutor,
                    objectMapper,
                    shadowMapper,
                    null);

            final AgentSpan span = buildTestSpan("span-001", "trace-001");
            span.setRequestBody("{\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}");
            final ShadowConfig config = buildTestConfig(1L, "unknown-model");

            when(shadowChatClientRegistry.getClient("unknown-model")).thenReturn(Optional.empty());

            final ShadowResult errorResult =
                    ShadowResult.builder().id(1L).status("ERROR").build();
            when(shadowResultService.save(any(ShadowResult.class))).thenReturn(errorResult);

            // when
            final CompletableFuture<ShadowResult> future = service.executeAsync(span, config);
            future.join();

            // then
            final ArgumentCaptor<ShadowResult> captor = ArgumentCaptor.forClass(ShadowResult.class);
            verify(shadowResultService).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("ERROR");
            assertThat(captor.getValue().getErrorMessage()).contains("ChatClient not found");
        }
    }

    @Nested
    @DisplayName("executeManual()")
    class ExecuteManual {

        @Test
        @DisplayName("should throw 404 when span not found")
        void should_Throw404_When_SpanNotFound() {
            // given
            final ShadowExecutionService service = new ShadowExecutionService(
                    shadowChatClientRegistry,
                    shadowResultService,
                    agentSpanRepository,
                    shadowExecutor,
                    objectMapper,
                    shadowMapper,
                    shadowEvaluationService);

            final ShadowExecuteRequest request = ShadowExecuteRequest.builder()
                    .spanId("non-existent-span")
                    .modelId("gpt-4")
                    .providerName("openrouter")
                    .build();
            when(agentSpanRepository.findBySpanId("non-existent-span")).thenReturn(Optional.empty());

            // when & then
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.executeManual(request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Span not found");
        }

        @Test
        @DisplayName("should save error result when request body has no prompt content")
        void should_SaveErrorResult_When_RequestBodyEmpty() {
            // given
            final ShadowExecutionService service = new ShadowExecutionService(
                    shadowChatClientRegistry,
                    shadowResultService,
                    agentSpanRepository,
                    shadowExecutor,
                    objectMapper,
                    shadowMapper,
                    shadowEvaluationService);

            final AgentSpan span = buildTestSpan("span-001", "trace-001");
            span.setRequestBody(null);
            when(agentSpanRepository.findBySpanId("span-001")).thenReturn(Optional.of(span));

            final ShadowResult errorResult =
                    ShadowResult.builder().id(1L).status("ERROR").build();
            when(shadowResultService.save(any(ShadowResult.class))).thenReturn(errorResult);

            final ShadowResultResponse responseDto =
                    ShadowResultResponse.builder().id(1L).status("ERROR").build();
            when(shadowMapper.toResultResponse(errorResult)).thenReturn(responseDto);

            final ShadowExecuteRequest request = ShadowExecuteRequest.builder()
                    .spanId("span-001")
                    .modelId("gpt-4")
                    .providerName("openrouter")
                    .build();

            // when
            final ShadowResultResponse response = service.executeManual(request);

            // then
            assertThat(response.getStatus()).isEqualTo("ERROR");
        }
    }

    /**
     * Строит тестовый AgentSpan.
     *
     * @param spanId  идентификатор span-а
     * @param traceId идентификатор трейса
     * @return тестовый AgentSpan
     */
    private AgentSpan buildTestSpan(final String spanId, final String traceId) {
        return AgentSpan.builder()
                .spanId(spanId)
                .traceId(traceId)
                .operationName("POST /v1/completions")
                .startedAt(Instant.now())
                .durationMicros(1000L)
                .requestBody("{\"messages\":[{\"role\":\"user\",\"content\":\"Hello\"}]}")
                .responseBody("Hello there!")
                .build();
    }

    /**
     * Строит тестовую ShadowConfig.
     *
     * @param id      идентификатор конфигурации
     * @param modelId идентификатор модели
     * @return тестовая ShadowConfig
     */
    private ShadowConfig buildTestConfig(final Long id, final String modelId) {
        return ShadowConfig.builder()
                .id(id)
                .serviceName("agent-service")
                .providerName("openrouter")
                .modelId(modelId)
                .samplingRate(new BigDecimal("100.00"))
                .status("ACTIVE")
                .build();
    }
}
