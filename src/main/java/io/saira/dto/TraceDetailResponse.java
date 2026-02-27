package io.saira.dto;

import java.time.Instant;
import java.util.List;

/** Детализированная информация о трейсе со списком спанов. */
public record TraceDetailResponse(
        String traceId,
        String serviceName,
        String fpId,
        String fpModuleId,
        Instant startedAt,
        Instant endedAt,
        Integer spanCount,
        String status,
        Instant createdAt,
        List<SpanResponse> spans) {}
