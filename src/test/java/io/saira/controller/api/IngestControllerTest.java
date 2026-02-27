package io.saira.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.saira.config.SecurityConfig;
import io.saira.dto.IngestResponse;
import io.saira.service.ingest.IngestService;

/** Тесты REST endpoint для приёма OTel телеметрии. */
@WebMvcTest(IngestController.class)
@Import(SecurityConfig.class)
class IngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestService ingestService;

    @Test
    @DisplayName("POST /api/v1/ingest — возвращает 202 при валидном OTLP JSON")
    void should_Return202_When_ValidOtlpJson() throws Exception {
        // given
        final String validJson = "{\"resourceSpans\":[{\"resource\":{\"attributes\":[{\"key\":\"service.name\","
                + "\"value\":{\"stringValue\":\"test\"}}]},\"scopeSpans\":[{\"spans\":[]}]}]}";

        final IngestResponse response = IngestResponse.builder()
                .traceCount(1)
                .spanCount(0)
                .filteredOutCount(0)
                .build();
        when(ingestService.ingest(any(ExportTraceServiceRequest.class))).thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.traceCount").value(1))
                .andExpect(jsonPath("$.spanCount").value(0))
                .andExpect(jsonPath("$.filteredOutCount").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/ingest — возвращает 400 при невалидном JSON")
    void should_Return400_When_InvalidJson() throws Exception {
        // given
        final String invalidJson = "{invalid";

        // when & then
        mockMvc.perform(post("/api/v1/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/ingest — возвращает 400 при пустом теле запроса")
    void should_Return400_When_EmptyBody() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }
}
