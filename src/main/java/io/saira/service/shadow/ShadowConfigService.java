package io.saira.service.shadow;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.saira.dto.ShadowConfigRequest;
import io.saira.dto.ShadowConfigResponse;
import io.saira.dto.mapper.ShadowMapper;
import io.saira.entity.ShadowConfig;
import io.saira.repository.ShadowConfigRepository;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис CRUD-операций над shadow-правилами.
 * Управляет жизненным циклом ShadowConfig и регистрацией ChatClient в реестре.
 */
@Slf4j
@Service
public class ShadowConfigService {

    /** Статус активного правила. */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /** Статус отключённого правила. */
    private static final String STATUS_DISABLED = "DISABLED";

    /** Имя кэша активных конфигураций. */
    private static final String CACHE_ACTIVE_CONFIGS = "shadow-active-configs";

    /** Репозиторий shadow-конфигураций. */
    private final ShadowConfigRepository shadowConfigRepository;

    /** Маппер сущностей в DTO. */
    private final ShadowMapper shadowMapper;

    /** Реестр ChatClient для shadow-вызовов (null если shadow отключён). */
    @Nullable private final ShadowChatClientRegistry shadowChatClientRegistry;

    /** Jackson ObjectMapper для сериализации modelParams. */
    private final ObjectMapper objectMapper;

    /**
     * Конструктор с опциональной зависимостью от ShadowChatClientRegistry.
     * Реестр может отсутствовать, если saira.shadow.enabled=false.
     *
     * @param shadowConfigRepository репозиторий конфигураций
     * @param shadowMapper           маппер сущностей в DTO
     * @param shadowChatClientRegistry реестр ChatClient (может быть null)
     * @param objectMapper           Jackson ObjectMapper
     */
    public ShadowConfigService(
            final ShadowConfigRepository shadowConfigRepository,
            final ShadowMapper shadowMapper,
            @Autowired(required = false) @Nullable final ShadowChatClientRegistry shadowChatClientRegistry,
            final ObjectMapper objectMapper) {
        this.shadowConfigRepository = shadowConfigRepository;
        this.shadowMapper = shadowMapper;
        this.shadowChatClientRegistry = shadowChatClientRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Создаёт новое shadow-правило и регистрирует ChatClient в реестре.
     *
     * @param request данные нового правила
     * @return созданное правило
     */
    @Transactional
    @CacheEvict(value = CACHE_ACTIVE_CONFIGS, allEntries = true)
    public ShadowConfigResponse createConfig(final ShadowConfigRequest request) {
        final ShadowConfig entity = mapRequestToEntity(request);
        entity.setStatus(STATUS_ACTIVE);
        final ShadowConfig saved = shadowConfigRepository.save(entity);

        registerChatClient(saved, request.getModelParams());
        log.info(
                "Создано shadow-правило id={}, service={}, model={}",
                saved.getId(),
                saved.getServiceName(),
                saved.getModelId());

        return shadowMapper.toConfigResponse(saved);
    }

    /**
     * Обновляет существующее shadow-правило.
     * Перерегистрирует ChatClient при смене модели/параметров.
     *
     * @param id      идентификатор правила
     * @param request новые данные правила
     * @return обновлённое правило
     */
    @Transactional
    @CacheEvict(value = CACHE_ACTIVE_CONFIGS, allEntries = true)
    public ShadowConfigResponse updateConfig(final Long id, final ShadowConfigRequest request) {
        final ShadowConfig existing = findOrThrow(id);
        final String oldModelId = existing.getModelId();

        existing.setServiceName(request.getServiceName());
        existing.setProviderName(request.getProviderName());
        existing.setModelId(request.getModelId());
        existing.setModelParams(serializeModelParams(request.getModelParams()));
        existing.setSamplingRate(request.getSamplingRate());

        final ShadowConfig saved = shadowConfigRepository.save(existing);

        unregisterChatClient(oldModelId);
        registerChatClient(saved, request.getModelParams());
        log.info("Обновлено shadow-правило id={}, model={}", saved.getId(), saved.getModelId());

        return shadowMapper.toConfigResponse(saved);
    }

    /**
     * Мягко удаляет shadow-правило (устанавливает статус DISABLED).
     * Удаляет ChatClient из реестра.
     *
     * @param id идентификатор правила
     */
    @Transactional
    @CacheEvict(value = CACHE_ACTIVE_CONFIGS, allEntries = true)
    public void deleteConfig(final Long id) {
        final ShadowConfig existing = findOrThrow(id);
        existing.setStatus(STATUS_DISABLED);
        shadowConfigRepository.save(existing);

        unregisterChatClient(existing.getModelId());
        log.info("Деактивировано shadow-правило id={}, model={}", id, existing.getModelId());
    }

    /**
     * Возвращает shadow-правило по идентификатору.
     *
     * @param id идентификатор правила
     * @return данные правила
     */
    @Transactional(readOnly = true)
    public ShadowConfigResponse getConfig(final Long id) {
        final ShadowConfig entity = findOrThrow(id);
        return shadowMapper.toConfigResponse(entity);
    }

    /**
     * Возвращает все shadow-правила.
     *
     * @return список всех правил
     */
    @Transactional(readOnly = true)
    public List<ShadowConfigResponse> listConfigs() {
        final List<ShadowConfig> configs = shadowConfigRepository.findAll();
        return shadowMapper.toConfigResponseList(configs);
    }

    /**
     * Возвращает активные конфигурации для указанного сервиса (с кэшированием).
     *
     * @param serviceName имя сервиса
     * @return список активных конфигураций
     */
    @Cacheable(value = CACHE_ACTIVE_CONFIGS)
    @Transactional(readOnly = true)
    public List<ShadowConfig> getActiveConfigsForService(final String serviceName) {
        return shadowConfigRepository.findByServiceNameAndStatus(serviceName, STATUS_ACTIVE);
    }

    /**
     * Возвращает все активные конфигурации.
     *
     * @return список активных конфигураций
     */
    @Transactional(readOnly = true)
    public List<ShadowConfig> listActiveConfigs() {
        return shadowConfigRepository.findAllByStatus(STATUS_ACTIVE);
    }

    /**
     * Находит конфигурацию по ID или выбрасывает 404.
     *
     * @param id идентификатор правила
     * @return найденная конфигурация
     */
    private ShadowConfig findOrThrow(final Long id) {
        return shadowConfigRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shadow config not found: " + id));
    }

    /**
     * Маппит ShadowConfigRequest в ShadowConfig entity.
     *
     * @param request данные запроса
     * @return новая сущность (без id и timestamps)
     */
    private ShadowConfig mapRequestToEntity(final ShadowConfigRequest request) {
        return ShadowConfig.builder()
                .serviceName(request.getServiceName())
                .providerName(request.getProviderName())
                .modelId(request.getModelId())
                .modelParams(serializeModelParams(request.getModelParams()))
                .samplingRate(request.getSamplingRate())
                .build();
    }

    /**
     * Регистрирует ChatClient для shadow-модели в реестре.
     * Пропускает регистрацию, если реестр недоступен (shadow отключён).
     *
     * @param config сохранённая конфигурация
     * @param params параметры модели
     */
    private void registerChatClient(final ShadowConfig config, final Map<String, Object> params) {
        if (shadowChatClientRegistry == null) {
            log.debug("Shadow реестр недоступен, пропуск регистрации ChatClient для модели {}", config.getModelId());
            return;
        }
        try {
            shadowChatClientRegistry.createClientForModel(config.getProviderName(), config.getModelId(), params);
        } catch (IllegalArgumentException e) {
            log.warn("Не удалось зарегистрировать ChatClient для модели {}: {}", config.getModelId(), e.getMessage());
        }
    }

    /**
     * Удаляет ChatClient из реестра.
     * Пропускает удаление, если реестр недоступен (shadow отключён).
     *
     * @param modelId идентификатор модели
     */
    private void unregisterChatClient(final String modelId) {
        if (shadowChatClientRegistry == null) {
            return;
        }
        shadowChatClientRegistry.unregisterClient(modelId);
    }

    /**
     * Сериализует Map параметров в JSON-строку.
     *
     * @param params карта параметров (может быть null)
     * @return JSON-строка или null
     */
    private String serializeModelParams(final Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid modelParams: " + e.getMessage());
        }
    }

    /**
     * Десериализует JSON-строку параметров в Map.
     *
     * @param json JSON-строка (может быть null)
     * @return карта параметров или null
     */
    @SuppressWarnings("unused")
    private Map<String, Object> deserializeModelParams(final String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Невалидный JSON modelParams: {}", e.getMessage());
            return null;
        }
    }
}
