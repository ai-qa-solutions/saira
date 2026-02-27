package io.saira.dto;

import java.time.Instant;

/** Элемент списка трейсов для REST API. */
public record TraceListItemResponse(
        String traceId,
        String serviceName,
        String fpId,
        String fpModuleId,
        Instant startedAt,
        Instant endedAt,
        Integer spanCount,
        String status,
        Instant createdAt) {}
