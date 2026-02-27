package io.saira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Ответ на запрос ingestion -- статистика обработки batch-а. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestResponse {

    /** Количество обработанных трейсов. */
    private int traceCount;

    /** Количество сохранённых span-ов (прошедших фильтрацию). */
    private int spanCount;

    /** Количество отфильтрованных span-ов. */
    private int filteredOutCount;
}
