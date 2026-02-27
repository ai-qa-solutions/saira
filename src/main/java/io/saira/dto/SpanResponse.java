package io.saira.dto;

import java.time.Instant;

/** Детали спана для REST API. */
public record SpanResponse(
        String spanId,
        String parentSpanId,
        String operationName,
        String spanKind,
        Instant startedAt,
        Long durationMicros,
        String statusCode,
        String httpUrl,
        String httpMethod,
        String httpStatus,
        String clientName,
        String requestBody,
        String responseBody,
        String outcome) {}
