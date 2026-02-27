package io.saira.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.saira.dto.ModelInfo;
import io.saira.dto.ProviderInfo;
import io.saira.service.shadow.ModelDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST контроллер для обнаружения AI-провайдеров и их моделей.
 * Используется в Settings UI для выбора модели при создании shadow-правила.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/shadow")
@RequiredArgsConstructor
public class ModelDiscoveryController {

    /** Сервис обнаружения моделей от провайдеров. */
    private final ModelDiscoveryService modelDiscoveryService;

    /**
     * Получить список подключённых AI-провайдеров.
     *
     * @return список провайдеров с enabled=true
     */
    @GetMapping("/providers")
    public ResponseEntity<List<ProviderInfo>> getProviders() {
        final List<ProviderInfo> providers = modelDiscoveryService.getConnectedProviders();
        return ResponseEntity.ok(providers);
    }

    /**
     * Получить список моделей от указанного провайдера.
     *
     * @param providerName имя провайдера (gigachat, openrouter)
     * @return список доступных моделей
     * @throws ResponseStatusException 404 если провайдер не найден или отключен
     */
    @GetMapping("/providers/{providerName}/models")
    public ResponseEntity<List<ModelInfo>> getModels(@PathVariable final String providerName) {
        if (!modelDiscoveryService.isProviderConnected(providerName)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Provider not found or not enabled: " + providerName);
        }

        final List<ModelInfo> models = modelDiscoveryService.listModels(providerName);
        return ResponseEntity.ok(models);
    }
}
