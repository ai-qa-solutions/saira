package io.saira.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import io.saira.config.SecurityConfig;
import io.saira.dto.ShadowConfigRequest;
import io.saira.dto.ShadowConfigResponse;
import io.saira.service.shadow.ShadowConfigService;

/** Тесты REST контроллера CRUD-операций над shadow-правилами. */
@WebMvcTest(ShadowConfigController.class)
@Import(SecurityConfig.class)
class ShadowConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShadowConfigService shadowConfigService;

    @Nested
    @DisplayName("POST /api/v1/shadow/configs")
    class CreateConfig {

        @Test
        @DisplayName("should return 201 Created with valid body")
        void should_Return201_When_ValidRequest() throws Exception {
            // given
            final ShadowConfigResponse response = ShadowConfigResponse.builder()
                    .id(1L)
                    .serviceName("agent-service")
                    .providerName("openrouter")
                    .modelId("gpt-4")
                    .samplingRate(new BigDecimal("50.00"))
                    .status("ACTIVE")
                    .createdAt(Instant.parse("2026-02-24T10:00:00Z"))
                    .build();
            when(shadowConfigService.createConfig(any(ShadowConfigRequest.class)))
                    .thenReturn(response);

            final String requestJson =
                    """
                    {
                        "serviceName": "agent-service",
                        "providerName": "openrouter",
                        "modelId": "gpt-4",
                        "samplingRate": 50.00
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/shadow/configs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.serviceName").value("agent-service"))
                    .andExpect(jsonPath("$.providerName").value("openrouter"))
                    .andExpect(jsonPath("$.modelId").value("gpt-4"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("should return 400 Bad Request when serviceName is missing")
        void should_Return400_When_ServiceNameMissing() throws Exception {
            // given
            final String requestJson =
                    """
                    {
                        "providerName": "openrouter",
                        "modelId": "gpt-4",
                        "samplingRate": 50.00
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/shadow/configs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 Bad Request when samplingRate exceeds 100")
        void should_Return400_When_SamplingRateExceeds100() throws Exception {
            // given
            final String requestJson =
                    """
                    {
                        "serviceName": "agent-service",
                        "providerName": "openrouter",
                        "modelId": "gpt-4",
                        "samplingRate": 150.00
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/shadow/configs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 Bad Request when samplingRate is negative")
        void should_Return400_When_SamplingRateNegative() throws Exception {
            // given
            final String requestJson =
                    """
                    {
                        "serviceName": "agent-service",
                        "providerName": "openrouter",
                        "modelId": "gpt-4",
                        "samplingRate": -1.00
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/shadow/configs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 Bad Request when modelId is missing")
        void should_Return400_When_ModelIdMissing() throws Exception {
            // given
            final String requestJson =
                    """
                    {
                        "serviceName": "agent-service",
                        "providerName": "openrouter",
                        "samplingRate": 50.00
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/shadow/configs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/shadow/configs/{id}")
    class UpdateConfig {

        @Test
        @DisplayName("should return 200 OK when valid update")
        void should_Return200_When_ValidUpdate() throws Exception {
            // given
            final ShadowConfigResponse response = ShadowConfigResponse.builder()
                    .id(1L)
                    .serviceName("agent-service")
                    .providerName("openrouter")
                    .modelId("gpt-4-turbo")
                    .samplingRate(new BigDecimal("75.00"))
                    .status("ACTIVE")
                    .build();
            when(shadowConfigService.updateConfig(eq(1L), any(ShadowConfigRequest.class)))
                    .thenReturn(response);

            final String requestJson =
                    """
                    {
                        "serviceName": "agent-service",
                        "providerName": "openrouter",
                        "modelId": "gpt-4-turbo",
                        "samplingRate": 75.00
                    }
                    """;

            // when & then
            mockMvc.perform(put("/api/v1/shadow/configs/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.modelId").value("gpt-4-turbo"));
        }

        @Test
        @DisplayName("should return 404 when config not found")
        void should_Return404_When_ConfigNotFound() throws Exception {
            // given
            when(shadowConfigService.updateConfig(eq(99L), any(ShadowConfigRequest.class)))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Shadow config not found: 99"));

            final String requestJson =
                    """
                    {
                        "serviceName": "agent-service",
                        "providerName": "openrouter",
                        "modelId": "gpt-4",
                        "samplingRate": 50.00
                    }
                    """;

            // when & then
            mockMvc.perform(put("/api/v1/shadow/configs/99")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/shadow/configs/{id}")
    class DeleteConfig {

        @Test
        @DisplayName("should return 204 No Content")
        void should_Return204_When_ConfigDeleted() throws Exception {
            // when & then
            mockMvc.perform(delete("/api/v1/shadow/configs/1")).andExpect(status().isNoContent());

            verify(shadowConfigService).deleteConfig(1L);
        }

        @Test
        @DisplayName("should return 404 when config not found")
        void should_Return404_When_ConfigNotFound() throws Exception {
            // given
            org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"))
                    .when(shadowConfigService)
                    .deleteConfig(99L);

            // when & then
            mockMvc.perform(delete("/api/v1/shadow/configs/99")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/shadow/configs")
    class ListConfigs {

        @Test
        @DisplayName("should return 200 OK with list of configs")
        void should_Return200_When_ConfigsExist() throws Exception {
            // given
            final List<ShadowConfigResponse> configs = List.of(
                    ShadowConfigResponse.builder()
                            .id(1L)
                            .serviceName("agent-service")
                            .modelId("gpt-4")
                            .status("ACTIVE")
                            .build(),
                    ShadowConfigResponse.builder()
                            .id(2L)
                            .serviceName("agent-service")
                            .modelId("claude-3")
                            .status("DISABLED")
                            .build());
            when(shadowConfigService.listConfigs()).thenReturn(configs);

            // when & then
            mockMvc.perform(get("/api/v1/shadow/configs").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].modelId").value("gpt-4"))
                    .andExpect(jsonPath("$[1].modelId").value("claude-3"));
        }

        @Test
        @DisplayName("should return 200 OK with empty list when no configs")
        void should_Return200_When_NoConfigs() throws Exception {
            // given
            when(shadowConfigService.listConfigs()).thenReturn(List.of());

            // when & then
            mockMvc.perform(get("/api/v1/shadow/configs").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/shadow/configs/{id}")
    class GetConfig {

        @Test
        @DisplayName("should return 200 OK when config found")
        void should_Return200_When_ConfigExists() throws Exception {
            // given
            final ShadowConfigResponse response = ShadowConfigResponse.builder()
                    .id(1L)
                    .serviceName("agent-service")
                    .providerName("openrouter")
                    .modelId("gpt-4")
                    .samplingRate(new BigDecimal("50.00"))
                    .status("ACTIVE")
                    .build();
            when(shadowConfigService.getConfig(1L)).thenReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/shadow/configs/1").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.modelId").value("gpt-4"))
                    .andExpect(jsonPath("$.samplingRate").value(50.00));
        }

        @Test
        @DisplayName("should return 404 when config not found")
        void should_Return404_When_ConfigNotFound() throws Exception {
            // given
            when(shadowConfigService.getConfig(99L))
                    .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));

            // when & then
            mockMvc.perform(get("/api/v1/shadow/configs/99").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }
}
