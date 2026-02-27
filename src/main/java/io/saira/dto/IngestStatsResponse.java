package io.saira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Статистика ingestion -- общее количество данных в системе. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestStatsResponse {

    /** Общее количество трейсов. */
    private long totalTraces;

    /** Общее количество span-ов. */
    private long totalSpans;

    /** Количество трейсов за последний час. */
    private long tracesLastHour;

    /** Количество span-ов за последний час. */
    private long spansLastHour;
}
