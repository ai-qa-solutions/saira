package io.saira.service.shadow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.saira.dto.ShadowConfigRequest;
import io.saira.dto.ShadowConfigResponse;
import io.saira.dto.mapper.ShadowMapper;
import io.saira.entity.ShadowConfig;
import io.saira.repository.ShadowConfigRepository;

/** Unit-тесты CRUD-сервиса shadow-конфигураций. */
@ExtendWith(MockitoExtension.class)
class ShadowConfigServiceTest {

    @Mock
    private ShadowConfigRepository shadowConfigRepository;

    @Mock
    private ShadowMapper shadowMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("createConfig()")
    class CreateConfig {

        @Test
        @DisplayName("should save config with ACTIVE status")
        void should_SaveConfig_When_ValidRequest() {
            // given
            final ShadowConfigService service =
                    new ShadowConfigService(shadowConfigRepository, shadowMapper, objectMapper);

            final ShadowConfigRequest request = ShadowConfigRequest.builder()
                    .serviceName("agent-service")
                    .providerName("openrouter")
                    .modelId("gpt-4")
                    .samplingRate(new BigDecimal("50.00"))
                    .build();

            final ShadowConfig savedEntity = ShadowConfig.builder()
                    .id(1L)
                    .serviceName("agent-service")
                    .providerName("openrouter")
                    .modelId("gpt-4")
                    .samplingRate(new BigDecimal("50.00"))
                    .status("ACTIVE")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            when(shadowConfigRepository.save(any(ShadowConfig.class))).thenReturn(savedEntity);

            final ShadowConfigResponse expectedResponse = ShadowConfigResponse.builder()
                    .id(1L)
                    .serviceName("agent-service")
                    .providerName("openrouter")
                    .modelId("gpt-4")
                    .status("ACTIVE")
                    .build();
            when(shadowMapper.toConfigResponse(savedEntity)).thenReturn(expectedResponse);

            // when
            final ShadowConfigResponse response = service.createConfig(request);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getStatus()).isEqualTo("ACTIVE");

            final ArgumentCaptor<ShadowConfig> captor = ArgumentCaptor.forClass(ShadowConfig.class);
            verify(shadowConfigRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        }
    }

    @Nested
    @DisplayName("updateConfig()")
    class UpdateConfig {

        @Test
        @DisplayName("should update config fields and save")
        void should_UpdateConfig_When_ValidRequest() {
            // given
            final ShadowConfigService service =
                    new ShadowConfigService(shadowConfigRepository, shadowMapper, objectMapper);

            final ShadowConfig existing = ShadowConfig.builder()
                    .id(1L)
                    .serviceName("agent-service")
                    .providerName("openrouter")
                    .modelId("gpt-3.5")
                    .samplingRate(new BigDecimal("50.00"))
                    .status("ACTIVE")
                    .build();
            when(shadowConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(shadowConfigRepository.save(any(ShadowConfig.class))).thenReturn(existing);
            when(shadowMapper.toConfigResponse(existing))
                    .thenReturn(ShadowConfigResponse.builder()
                            .id(1L)
                            .modelId("gpt-4")
                            .build());

            final ShadowConfigRequest request = ShadowConfigRequest.builder()
                    .serviceName("agent-service")
                    .providerName("openrouter")
                    .modelId("gpt-4")
                    .samplingRate(new BigDecimal("75.00"))
                    .build();

            // when
            final ShadowConfigResponse response = service.updateConfig(1L, request);

            // then
            assertThat(response).isNotNull();
            verify(shadowConfigRepository).save(any(ShadowConfig.class));
        }

        @Test
        @DisplayName("should throw 404 when config not found")
        void should_Throw404_When_ConfigNotFound() {
            // given
            final ShadowConfigService service =
                    new ShadowConfigService(shadowConfigRepository, shadowMapper, objectMapper);
            when(shadowConfigRepository.findById(99L)).thenReturn(Optional.empty());

            final ShadowConfigRequest request = ShadowConfigRequest.builder()
                    .serviceName("svc")
                    .providerName("openrouter")
                    .modelId("gpt-4")
                    .samplingRate(BigDecimal.TEN)
                    .build();

            // when & then
            assertThatThrownBy(() -> service.updateConfig(99L, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("deleteConfig()")
    class DeleteConfig {

        @Test
        @DisplayName("should set status DISABLED")
        void should_DisableConfig() {
            // given
            final ShadowConfigService service =
                    new ShadowConfigService(shadowConfigRepository, shadowMapper, objectMapper);

            final ShadowConfig existing = ShadowConfig.builder()
                    .id(1L)
                    .serviceName("agent-service")
                    .providerName("openrouter")
                    .modelId("gpt-4")
                    .status("ACTIVE")
                    .build();
            when(shadowConfigRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(shadowConfigRepository.save(any(ShadowConfig.class))).thenReturn(existing);

            // when
            service.deleteConfig(1L);

            // then
            final ArgumentCaptor<ShadowConfig> captor = ArgumentCaptor.forClass(ShadowConfig.class);
            verify(shadowConfigRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo("DISABLED");
        }

        @Test
        @DisplayName("should throw 404 when config not found")
        void should_Throw404_When_ConfigNotFound() {
            // given
            final ShadowConfigService service =
                    new ShadowConfigService(shadowConfigRepository, shadowMapper, objectMapper);
            when(shadowConfigRepository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.deleteConfig(99L))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("getConfig()")
    class GetConfig {

        @Test
        @DisplayName("should return config response when found")
        void should_ReturnResponse_When_ConfigExists() {
            // given
            final ShadowConfigService service =
                    new ShadowConfigService(shadowConfigRepository, shadowMapper, objectMapper);

            final ShadowConfig entity = ShadowConfig.builder()
                    .id(1L)
                    .serviceName("agent-service")
                    .modelId("gpt-4")
                    .build();
            when(shadowConfigRepository.findById(1L)).thenReturn(Optional.of(entity));

            final ShadowConfigResponse expected =
                    ShadowConfigResponse.builder().id(1L).modelId("gpt-4").build();
            when(shadowMapper.toConfigResponse(entity)).thenReturn(expected);

            // when
            final ShadowConfigResponse response = service.getConfig(1L);

            // then
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw 404 when config not found")
        void should_Throw404_When_NotFound() {
            // given
            final ShadowConfigService service =
                    new ShadowConfigService(shadowConfigRepository, shadowMapper, objectMapper);
            when(shadowConfigRepository.findById(99L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.getConfig(99L))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("listConfigs()")
    class ListConfigs {

        @Test
        @DisplayName("should return all configs")
        void should_ReturnAllConfigs() {
            // given
            final ShadowConfigService service =
                    new ShadowConfigService(shadowConfigRepository, shadowMapper, objectMapper);

            final List<ShadowConfig> entities = List.of(
                    ShadowConfig.builder().id(1L).modelId("gpt-4").build(),
                    ShadowConfig.builder().id(2L).modelId("claude-3").build());
            when(shadowConfigRepository.findAll()).thenReturn(entities);

            final List<ShadowConfigResponse> expected = List.of(
                    ShadowConfigResponse.builder().id(1L).build(),
                    ShadowConfigResponse.builder().id(2L).build());
            when(shadowMapper.toConfigResponseList(entities)).thenReturn(expected);

            // when
            final List<ShadowConfigResponse> result = service.listConfigs();

            // then
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getActiveConfigsForService()")
    class GetActiveConfigsForService {

        @Test
        @DisplayName("should return active configs for given service")
        void should_ReturnActiveConfigs_When_ServiceHasConfigs() {
            // given
            final ShadowConfigService service =
                    new ShadowConfigService(shadowConfigRepository, shadowMapper, objectMapper);

            final List<ShadowConfig> activeConfigs = List.of(ShadowConfig.builder()
                    .id(1L)
                    .serviceName("agent-service")
                    .modelId("gpt-4")
                    .status("ACTIVE")
                    .build());
            when(shadowConfigRepository.findByServiceNameAndStatus("agent-service", "ACTIVE"))
                    .thenReturn(activeConfigs);

            // when
            final List<ShadowConfig> result = service.getActiveConfigsForService("agent-service");

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getModelId()).isEqualTo("gpt-4");
        }

        @Test
        @DisplayName("should return empty list when no active configs")
        void should_ReturnEmptyList_When_NoActiveConfigs() {
            // given
            final ShadowConfigService service =
                    new ShadowConfigService(shadowConfigRepository, shadowMapper, objectMapper);
            when(shadowConfigRepository.findByServiceNameAndStatus("unknown", "ACTIVE"))
                    .thenReturn(List.of());

            // when
            final List<ShadowConfig> result = service.getActiveConfigsForService("unknown");

            // then
            assertThat(result).isEmpty();
        }
    }
}
