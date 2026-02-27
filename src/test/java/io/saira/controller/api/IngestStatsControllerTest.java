package io.saira.controller.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import io.saira.config.SecurityConfig;
import io.saira.repository.AgentSpanRepository;
import io.saira.repository.AgentTraceRepository;

/** Тесты endpoint статистики ingestion. */
@WebMvcTest(IngestStatsController.class)
@Import(SecurityConfig.class)
class IngestStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentTraceRepository traceRepository;

    @MockBean
    private AgentSpanRepository spanRepository;

    @Test
    @DisplayName("GET /api/v1/ingest/stats — возвращает статистику с данными")
    void should_ReturnStats_When_DataExists() throws Exception {
        // given
        when(traceRepository.count()).thenReturn(42L);
        when(spanRepository.count()).thenReturn(128L);
        when(traceRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(5L);
        when(spanRepository.countByStartedAtAfter(any(Instant.class))).thenReturn(15L);

        // when & then
        mockMvc.perform(get("/api/v1/ingest/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTraces").value(42))
                .andExpect(jsonPath("$.totalSpans").value(128))
                .andExpect(jsonPath("$.tracesLastHour").value(5))
                .andExpect(jsonPath("$.spansLastHour").value(15));
    }
}
