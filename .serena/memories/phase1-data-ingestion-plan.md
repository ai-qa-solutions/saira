# Phase 1: Data Ingestion Plan

## Key Decisions
- Kafka topic: `fp-saira-spans` (from docker-compose)
- Kafka format: OTLP Protobuf binary (ByteArrayDeserializer)
- REST format: OTLP JSON (via protobuf JsonFormat)
- Manual ack for Kafka consumer (pattern from cm-oltp-service)
- Span filtering: only HTTP POST client/server + gen_ai spans saved
- JDBC, result-set, GET spans filtered out during ingestion
- No GET /api/v1/traces in Phase 1 (deferred to Phase 2)

## Dependencies Added
- io.opentelemetry.proto:opentelemetry-proto:1.3.2-alpha
- com.google.protobuf:protobuf-java:4.28.2
- com.google.protobuf:protobuf-java-util:4.28.2

## DB Tables (V2 Migration)
- agent_trace (trace_id, service_name, fp_id, fp_module_id, started_at, ended_at, span_count, status)
- agent_span (span_id, trace_id FK, parent_span_id, operation_name, span_kind, started_at, duration_micros, status_code)
- span_attribute (span_id FK, attr_key, attr_type, attr_value)

## Key Attributes from Real Data
- cm.otel.fp.id — project ID (e.g. giga-insurance)
- cm.otel.fp.module.id — module ID
- gen_ai.* — LLM call metadata
- http.request.body / http.response.body — request/response payloads
- span.kind — client/server/internal (in tags, not proto SpanKind)

## Reference Project
- cm-oltp-service: Kafka consumer pattern, proto parsing, filter chain
- Path: /Users/artemsimeisn/IdeaProjects/sber/cm-oltp-service

## Plan File
- specs/phase1-data-ingestion.md
