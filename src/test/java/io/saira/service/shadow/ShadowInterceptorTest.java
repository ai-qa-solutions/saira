package io.saira.service.shadow;

import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.saira.entity.AgentSpan;
import io.saira.entity.ShadowConfig;

/** Unit-тесты перехватчика span-ов для автоматических shadow-вызовов. */
@ExtendWith(MockitoExtension.class)
class ShadowInterceptorTest {

    @Mock
    private ShadowConfigService shadowConfigService;

    @Mock
    private ShadowExecutionService shadowExecutionService;

    @Nested
    @DisplayName("interceptSpan()")
    class InterceptSpan {

        @Test
        @DisplayName("should trigger shadow execution when active config matches")
        void should_TriggerExecution_When_ActiveConfigExists() {
            // given
            final ShadowInterceptor interceptor = new ShadowInterceptor(shadowConfigService, shadowExecutionService);

            final AgentSpan span = buildTestSpan("span-001", "trace-001", "request body content");
            final ShadowConfig config = buildTestConfig(1L, "gpt-4", new BigDecimal("100.00"));

            when(shadowConfigService.getActiveConfigsForService("agent-service"))
                    .thenReturn(List.of(config));

            // when
            interceptor.interceptSpan(span, "agent-service");

            // then — with 100% sampling rate, always triggers
            verify(shadowExecutionService).executeAsync(span, config);
        }

        @Test
        @DisplayName("should not trigger when sampling rate is 0%")
        void should_NotTrigger_When_SamplingRateZero() {
            // given
            final ShadowInterceptor interceptor = new ShadowInterceptor(shadowConfigService, shadowExecutionService);

            final AgentSpan span = buildTestSpan("span-001", "trace-001", "request body content");
            final ShadowConfig config = buildTestConfig(1L, "gpt-4", BigDecimal.ZERO);

            when(shadowConfigService.getActiveConfigsForService("agent-service"))
                    .thenReturn(List.of(config));

            // when
            interceptor.interceptSpan(span, "agent-service");

            // then — 0% sampling rate means random value is always >= 0, so never triggers
            verify(shadowExecutionService, never()).executeAsync(any(), any());
        }

        @Test
        @DisplayName("should not trigger when no active configs for service")
        void should_NotTrigger_When_NoActiveConfigs() {
            // given
            final ShadowInterceptor interceptor = new ShadowInterceptor(shadowConfigService, shadowExecutionService);

            final AgentSpan span = buildTestSpan("span-001", "trace-001", "request body content");
            when(shadowConfigService.getActiveConfigsForService("unknown-service"))
                    .thenReturn(List.of());

            // when
            interceptor.interceptSpan(span, "unknown-service");

            // then
            verify(shadowExecutionService, never()).executeAsync(any(), any());
        }

        @Test
        @DisplayName("should skip when requestBody is null")
        void should_Skip_When_RequestBodyNull() {
            // given
            final ShadowInterceptor interceptor = new ShadowInterceptor(shadowConfigService, shadowExecutionService);

            final AgentSpan span = buildTestSpan("span-001", "trace-001", null);

            // when
            interceptor.interceptSpan(span, "agent-service");

            // then
            verify(shadowConfigService, never()).getActiveConfigsForService(any());
            verify(shadowExecutionService, never()).executeAsync(any(), any());
        }

        @Test
        @DisplayName("should skip when requestBody is blank")
        void should_Skip_When_RequestBodyBlank() {
            // given
            final ShadowInterceptor interceptor = new ShadowInterceptor(shadowConfigService, shadowExecutionService);

            final AgentSpan span = buildTestSpan("span-001", "trace-001", "   ");

            // when
            interceptor.interceptSpan(span, "agent-service");

            // then
            verify(shadowConfigService, never()).getActiveConfigsForService(any());
            verify(shadowExecutionService, never()).executeAsync(any(), any());
        }

        @Test
        @DisplayName("should skip when shadowConfigService is null")
        void should_Skip_When_ConfigServiceNull() {
            // given
            final ShadowInterceptor interceptor = new ShadowInterceptor(null, shadowExecutionService);

            final AgentSpan span = buildTestSpan("span-001", "trace-001", "request body");

            // when
            interceptor.interceptSpan(span, "agent-service");

            // then
            verify(shadowExecutionService, never()).executeAsync(any(), any());
        }

        @Test
        @DisplayName("should skip when shadowExecutionService is null")
        void should_Skip_When_ExecutionServiceNull() {
            // given
            final ShadowInterceptor interceptor = new ShadowInterceptor(shadowConfigService, null);

            final AgentSpan span = buildTestSpan("span-001", "trace-001", "request body");

            // when
            interceptor.interceptSpan(span, "agent-service");

            // then
            verify(shadowConfigService, never()).getActiveConfigsForService(any());
        }

        @Test
        @DisplayName("should skip when both services are null")
        void should_Skip_When_BothServicesNull() {
            // given
            final ShadowInterceptor interceptor = new ShadowInterceptor(null, null);

            final AgentSpan span = buildTestSpan("span-001", "trace-001", "request body");

            // when
            interceptor.interceptSpan(span, "agent-service");

            // then — no exceptions, graceful skip
        }
    }

    /**
     * Строит тестовый AgentSpan.
     *
     * @param spanId      идентификатор span-а
     * @param traceId     идентификатор трейса
     * @param requestBody тело запроса
     * @return тестовый AgentSpan
     */
    private AgentSpan buildTestSpan(final String spanId, final String traceId, final String requestBody) {
        return AgentSpan.builder()
                .spanId(spanId)
                .traceId(traceId)
                .operationName("POST /v1/completions")
                .startedAt(Instant.now())
                .durationMicros(1000L)
                .requestBody(requestBody)
                .build();
    }

    /**
     * Строит тестовую ShadowConfig.
     *
     * @param id           идентификатор конфигурации
     * @param modelId      идентификатор модели
     * @param samplingRate процент выборки
     * @return тестовая ShadowConfig
     */
    private ShadowConfig buildTestConfig(final Long id, final String modelId, final BigDecimal samplingRate) {
        return ShadowConfig.builder()
                .id(id)
                .serviceName("agent-service")
                .providerName("openrouter")
                .modelId(modelId)
                .samplingRate(samplingRate)
                .status("ACTIVE")
                .build();
    }
}
