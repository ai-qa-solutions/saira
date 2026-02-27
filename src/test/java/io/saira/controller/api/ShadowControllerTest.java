package io.saira.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.saira.config.SecurityConfig;
import io.saira.dto.ShadowExecuteRequest;
import io.saira.dto.ShadowResultResponse;
import io.saira.dto.ShadowTraceItemResponse;
import io.saira.entity.ShadowEvaluation;
import io.saira.service.shadow.ShadowEvaluationService;
import io.saira.service.shadow.ShadowExecutionService;
import io.saira.service.shadow.ShadowResultService;

/** Тесты REST контроллера для shadow-вызовов и оценок. */
@WebMvcTest(ShadowController.class)
@Import(SecurityConfig.class)
class ShadowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShadowExecutionService shadowExecutionService;

    @MockitoBean
    private ShadowResultService shadowResultService;

    @MockitoBean
    private ShadowEvaluationService shadowEvaluationService;

    @Nested
    @DisplayName("GET /api/v1/shadow/results/{resultId}/evaluation")
    class GetEvaluation {

        @Test
        @DisplayName("should return 200 with evaluation when exists")
        void should_Return200WithEvaluation_When_EvaluationExists() throws Exception {
            // given
            final Instant now = Instant.parse("2026-02-24T10:00:00Z");
            final ShadowEvaluation evaluation = ShadowEvaluation.builder()
                    .id(1L)
                    .shadowResultId(42L)
                    .semanticSimilarity(new BigDecimal("0.9200"))
                    .correctnessScore(new BigDecimal("1.0000"))
                    .evaluationDetail("{\"semanticSimilarity\":0.92,\"correctnessScore\":1.0}")
                    .evaluatedAt(now)
                    .createdAt(now)
                    .build();
            when(shadowEvaluationService.getEvaluationForResult(42L)).thenReturn(Optional.of(evaluation));

            // when & then
            mockMvc.perform(get("/api/v1/shadow/results/42/evaluation").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shadowResultId").value(42))
                    .andExpect(jsonPath("$.semanticSimilarity").value(0.92))
                    .andExpect(jsonPath("$.correctnessScore").value(1.0))
                    .andExpect(jsonPath("$.evaluationDetail").isNotEmpty());
        }

        @Test
        @DisplayName("should return 404 when evaluation not found")
        void should_Return404_When_EvaluationNotFound() throws Exception {
            // given
            when(shadowEvaluationService.getEvaluationForResult(99L)).thenReturn(Optional.empty());

            // when & then
            mockMvc.perform(get("/api/v1/shadow/results/99/evaluation").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/shadow/execute")
    class Execute {

        @Test
        @DisplayName("should return 201 Created when shadow execution succeeds")
        void should_Return201_When_ExecutionSucceeds() throws Exception {
            // given
            final ShadowResultResponse response = ShadowResultResponse.builder()
                    .id(1L)
                    .sourceSpanId("span-001")
                    .modelId("gpt-4")
                    .status("SUCCESS")
                    .responseBody("Shadow response text")
                    .latencyMs(150L)
                    .build();
            when(shadowExecutionService.executeManual(any(ShadowExecuteRequest.class)))
                    .thenReturn(response);

            final String requestJson =
                    """
                    {
                        "spanId": "span-001",
                        "modelId": "gpt-4",
                        "providerName": "openrouter"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/shadow/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.sourceSpanId").value("span-001"))
                    .andExpect(jsonPath("$.modelId").value("gpt-4"))
                    .andExpect(jsonPath("$.status").value("SUCCESS"));
        }

        @Test
        @DisplayName("should return 400 when spanId is missing")
        void should_Return400_When_SpanIdMissing() throws Exception {
            // given
            final String requestJson =
                    """
                    {
                        "modelId": "gpt-4",
                        "providerName": "openrouter"
                    }
                    """;

            // when & then
            mockMvc.perform(post("/api/v1/shadow/execute")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/shadow/results/span/{spanId}")
    class GetResultsBySpan {

        @Test
        @DisplayName("should return 200 with results for span")
        void should_Return200_When_ResultsExist() throws Exception {
            // given
            final List<ShadowResultResponse> results = List.of(ShadowResultResponse.builder()
                    .id(1L)
                    .sourceSpanId("span-001")
                    .modelId("gpt-4")
                    .status("SUCCESS")
                    .build());
            when(shadowResultService.getResultsForSpan("span-001")).thenReturn(results);

            // when & then
            mockMvc.perform(get("/api/v1/shadow/results/span/span-001").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].sourceSpanId").value("span-001"));
        }

        @Test
        @DisplayName("should return 200 with empty list when no results")
        void should_Return200_When_NoResults() throws Exception {
            // given
            when(shadowResultService.getResultsForSpan("span-999")).thenReturn(List.of());

            // when & then
            mockMvc.perform(get("/api/v1/shadow/results/span/span-999").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/shadow/results/trace/{traceId}")
    class GetResultsByTrace {

        @Test
        @DisplayName("should return 200 with results for trace")
        void should_Return200_When_ResultsExist() throws Exception {
            // given
            final List<ShadowResultResponse> results = List.of(
                    ShadowResultResponse.builder()
                            .id(1L)
                            .sourceSpanId("span-001")
                            .modelId("gpt-4")
                            .build(),
                    ShadowResultResponse.builder()
                            .id(2L)
                            .sourceSpanId("span-002")
                            .modelId("claude-3")
                            .build());
            when(shadowResultService.getResultsForTrace("trace-001")).thenReturn(results);

            // when & then
            mockMvc.perform(get("/api/v1/shadow/results/trace/trace-001").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/shadow/traces")
    class GetShadowTraces {

        @Test
        @DisplayName("should return 200 with paginated shadow traces")
        void should_Return200_When_TracesExist() throws Exception {
            // given
            final ShadowTraceItemResponse item = ShadowTraceItemResponse.builder()
                    .traceId("trace-001")
                    .serviceName("test-service")
                    .shadowCount(3)
                    .latestModelId("gpt-4")
                    .build();
            final Page<ShadowTraceItemResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);
            when(shadowResultService.getTracesWithShadowResults(any())).thenReturn(page);

            // when & then
            mockMvc.perform(get("/api/v1/shadow/traces")
                            .param("page", "0")
                            .param("size", "20")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].traceId").value("trace-001"))
                    .andExpect(jsonPath("$.content[0].serviceName").value("test-service"))
                    .andExpect(jsonPath("$.content[0].shadowCount").value(3))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("should return 200 with empty page when no traces")
        void should_Return200_When_NoTraces() throws Exception {
            // given
            when(shadowResultService.getTracesWithShadowResults(any())).thenReturn(Page.empty());

            // when & then
            mockMvc.perform(get("/api/v1/shadow/traces").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0));
        }
    }
}
