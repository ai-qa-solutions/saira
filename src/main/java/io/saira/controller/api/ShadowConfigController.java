package io.saira.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.saira.dto.ShadowConfigRequest;
import io.saira.dto.ShadowConfigResponse;
import io.saira.service.shadow.ShadowConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST контроллер для CRUD-операций над shadow-правилами.
 * Управляет конфигурациями shadow-тестирования моделей.
 */
@RestController
@RequestMapping("/api/v1/shadow/configs")
@RequiredArgsConstructor
public class ShadowConfigController {

    /** Сервис управления shadow-конфигурациями. */
    private final ShadowConfigService shadowConfigService;

    /**
     * Создать новое shadow-правило.
     *
     * @param request данные нового правила
     * @return созданное правило (201 Created)
     */
    @PostMapping
    public ResponseEntity<ShadowConfigResponse> createConfig(@Valid @RequestBody final ShadowConfigRequest request) {
        final ShadowConfigResponse response = shadowConfigService.createConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Обновить существующее shadow-правило.
     *
     * @param id      идентификатор правила
     * @param request новые данные правила
     * @return обновлённое правило
     */
    @PutMapping("/{id}")
    public ShadowConfigResponse updateConfig(
            @PathVariable final Long id, @Valid @RequestBody final ShadowConfigRequest request) {
        return shadowConfigService.updateConfig(id, request);
    }

    /**
     * Удалить (деактивировать) shadow-правило.
     *
     * @param id идентификатор правила
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConfig(@PathVariable final Long id) {
        shadowConfigService.deleteConfig(id);
    }

    /**
     * Получить список всех shadow-правил.
     *
     * @return список всех правил
     */
    @GetMapping
    public List<ShadowConfigResponse> listConfigs() {
        return shadowConfigService.listConfigs();
    }

    /**
     * Получить shadow-правило по идентификатору.
     *
     * @param id идентификатор правила
     * @return данные правила
     */
    @GetMapping("/{id}")
    public ShadowConfigResponse getConfig(@PathVariable final Long id) {
        return shadowConfigService.getConfig(id);
    }
}
