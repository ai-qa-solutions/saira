package io.saira.controller.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.saira.dto.IngestStatsResponse;
import io.saira.repository.AgentSpanRepository;
import io.saira.repository.AgentTraceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Endpoint для статистики ingestion.
 * Показывает общее количество данных и данные за последний час.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestStatsController {

    /** Репозиторий трейсов. */
    private final AgentTraceRepository traceRepository;

    /** Репозиторий span-ов. */
    private final AgentSpanRepository spanRepository;

    /**
     * Получить статистику ingestion.
     *
     * @return общее количество трейсов и span-ов + за последний час
     */
    @GetMapping("/stats")
    public ResponseEntity<IngestStatsResponse> getStats() {
        final long totalTraces = traceRepository.count();
        final long totalSpans = spanRepository.count();
        final Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        final long tracesLastHour = traceRepository.countByCreatedAtAfter(oneHourAgo);
        final long spansLastHour = spanRepository.countByStartedAtAfter(oneHourAgo);

        final IngestStatsResponse response = IngestStatsResponse.builder()
                .totalTraces(totalTraces)
                .totalSpans(totalSpans)
                .tracesLastHour(tracesLastHour)
                .spansLastHour(spansLastHour)
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * Получить список уникальных имён сервисов.
     *
     * @return отсортированный список имён сервисов
     */
    @GetMapping("/service-names")
    public ResponseEntity<List<String>> getServiceNames() {
        List<String> serviceNames = traceRepository.findDistinctServiceNames();
        return ResponseEntity.ok(serviceNames);
    }
}
