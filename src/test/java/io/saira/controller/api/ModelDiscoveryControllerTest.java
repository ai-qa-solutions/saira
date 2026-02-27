package io.saira.controller.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.saira.config.SecurityConfig;
import io.saira.dto.ModelInfo;
import io.saira.dto.ProviderInfo;
import io.saira.service.shadow.ModelDiscoveryService;

/** Тесты REST контроллера обнаружения AI-провайдеров и моделей. */
@WebMvcTest(ModelDiscoveryController.class)
@Import(SecurityConfig.class)
class ModelDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ModelDiscoveryService modelDiscoveryService;

    @Nested
    @DisplayName("GET /api/v1/shadow/providers")
    class GetProviders {

        @Test
        @DisplayName("should return 200 OK with list of enabled providers")
        void should_Return200_When_ProvidersExist() throws Exception {
            // given
            final List<ProviderInfo> providers = List.of(
                    ProviderInfo.builder()
                            .name("openrouter")
                            .enabled(true)
                            .baseUrl("https://openrouter.ai/api")
                            .build(),
                    ProviderInfo.builder()
                            .name("gigachat")
                            .enabled(true)
                            .baseUrl("https://gigachat.devices.sberbank.ru/api")
                            .build());
            when(modelDiscoveryService.getConnectedProviders()).thenReturn(providers);

            // when & then
            mockMvc.perform(get("/api/v1/shadow/providers").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("openrouter"))
                    .andExpect(jsonPath("$[0].enabled").value(true))
                    .andExpect(jsonPath("$[1].name").value("gigachat"));
        }

        @Test
        @DisplayName("should return 200 OK with empty list when no providers")
        void should_Return200_When_NoProviders() throws Exception {
            // given
            when(modelDiscoveryService.getConnectedProviders()).thenReturn(List.of());

            // when & then
            mockMvc.perform(get("/api/v1/shadow/providers").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/shadow/providers/{providerName}/models")
    class GetModels {

        @Test
        @DisplayName("should return 200 OK with list of models")
        void should_Return200_When_ModelsExist() throws Exception {
            // given
            when(modelDiscoveryService.isProviderConnected("openrouter")).thenReturn(true);

            final List<ModelInfo> models = List.of(
                    ModelInfo.builder()
                            .id("anthropic/claude-3.5-sonnet")
                            .name("Claude 3.5 Sonnet")
                            .provider("openrouter")
                            .description("Anthropic's most capable model")
                            .contextLength(200000)
                            .build(),
                    ModelInfo.builder()
                            .id("openai/gpt-4")
                            .name("GPT-4")
                            .provider("openrouter")
                            .contextLength(128000)
                            .build());
            when(modelDiscoveryService.listModels("openrouter")).thenReturn(models);

            // when & then
            mockMvc.perform(get("/api/v1/shadow/providers/openrouter/models").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value("anthropic/claude-3.5-sonnet"))
                    .andExpect(jsonPath("$[0].name").value("Claude 3.5 Sonnet"))
                    .andExpect(jsonPath("$[0].contextLength").value(200000))
                    .andExpect(jsonPath("$[1].id").value("openai/gpt-4"));
        }

        @Test
        @DisplayName("should return 404 when provider not connected")
        void should_Return404_When_ProviderNotConnected() throws Exception {
            // given
            when(modelDiscoveryService.isProviderConnected("unknown")).thenReturn(false);

            // when & then
            mockMvc.perform(get("/api/v1/shadow/providers/unknown/models").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 200 OK with empty list when provider has no models")
        void should_Return200WithEmptyList_When_NoModels() throws Exception {
            // given
            when(modelDiscoveryService.isProviderConnected("gigachat")).thenReturn(true);
            when(modelDiscoveryService.listModels("gigachat")).thenReturn(List.of());

            // when & then
            mockMvc.perform(get("/api/v1/shadow/providers/gigachat/models").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
}
