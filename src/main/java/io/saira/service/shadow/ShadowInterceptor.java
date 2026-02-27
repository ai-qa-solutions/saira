package io.saira.service.shadow;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import io.saira.entity.AgentSpan;
import io.saira.entity.ShadowConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * Перехватчик span-ов при ingestion для автоматических shadow-вызовов.
 *
 * <p>Проверяет наличие активных shadow-правил для сервиса,
 * применяет sampling rate и запускает асинхронные shadow-вызовы.
 * Вызывается из IngestService после сохранения каждого span-а.
 */
@Slf4j
@Component
public class ShadowInterceptor {

    /** Сервис управления shadow-конфигурациями (null если shadow отключён). */
    @Nullable private final ShadowConfigService shadowConfigService;

    /** Сервис выполнения shadow-вызовов (null если shadow отключён). */
    @Nullable private final ShadowExecutionService shadowExecutionService;

    /**
     * Конструктор с опциональными зависимостями.
     * Оба сервиса могут отсутствовать, если shadow отключён.
     *
     * @param shadowConfigService    сервис конфигураций (может быть null)
     * @param shadowExecutionService сервис выполнения (может быть null)
     */
    public ShadowInterceptor(
            @Autowired(required = false) @Nullable final ShadowConfigService shadowConfigService,
            @Autowired(required = false) @Nullable final ShadowExecutionService shadowExecutionService) {
        this.shadowConfigService = shadowConfigService;
        this.shadowExecutionService = shadowExecutionService;
    }

    /**
     * Перехватывает span и запускает shadow-вызовы по активным правилам.
     *
     * <p>Пропускает span если:
     * <ul>
     *   <li>shadow-компоненты не доступны</li>
     *   <li>requestBody отсутствует</li>
     *   <li>нет активных правил для данного сервиса</li>
     *   <li>sampling rate не прошёл (случайное число больше порога)</li>
     * </ul>
     *
     * @param span        сохранённый AgentSpan
     * @param serviceName имя сервиса из OTel Resource
     */
    public void interceptSpan(final AgentSpan span, final String serviceName) {
        if (!isAvailable()) {
            return;
        }
        if (span.getRequestBody() == null || span.getRequestBody().isBlank()) {
            return;
        }

        final List<ShadowConfig> activeConfigs = shadowConfigService.getActiveConfigsForService(serviceName);
        if (activeConfigs.isEmpty()) {
            return;
        }

        for (final ShadowConfig config : activeConfigs) {
            triggerIfSampled(span, config);
        }
    }

    /**
     * Проверяет sampling rate и запускает shadow-вызов если span попал в выборку.
     *
     * @param span   оригинальный span
     * @param config shadow-конфигурация с sampling rate
     */
    private void triggerIfSampled(final AgentSpan span, final ShadowConfig config) {
        final double samplingRate = config.getSamplingRate().doubleValue();
        final double randomValue = ThreadLocalRandom.current().nextDouble() * 100;

        if (randomValue >= samplingRate) {
            log.trace(
                    "Span {} не попал в выборку для shadow config {} (rate={}%)",
                    span.getSpanId(), config.getId(), samplingRate);
            return;
        }

        log.debug(
                "Запуск shadow-вызова: span={}, model={}, config={}",
                span.getSpanId(),
                config.getModelId(),
                config.getId());

        shadowExecutionService.executeAsync(span, config);
    }

    /**
     * Проверяет доступность shadow-компонентов.
     *
     * @return true если оба сервиса доступны
     */
    private boolean isAvailable() {
        return shadowConfigService != null && shadowExecutionService != null;
    }
}
