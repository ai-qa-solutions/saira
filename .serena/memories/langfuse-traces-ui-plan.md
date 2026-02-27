# Langfuse Traces UI Plan

## What Was Planned
Full-stack Langfuse-like trace viewer for SAIRA:
- Backend: GET /api/v1/traces (paginated) + GET /api/v1/traces/{traceId} (detail with spans)
- Frontend: shadcn/ui data table + trace detail panel with span tree hierarchy

## Key Decisions
- DTOs: Java records (TraceListItemResponse, SpanResponse, TraceDetailResponse) — no MapStruct
- 404: ResponseStatusException(HttpStatus.NOT_FOUND) — no custom exception class
- Span tree: built client-side from flat span list using parentSpanId
- Style: corporate strict, monochrome, no emojis, shadcn/ui New York style
- Repos: added findAllByOrderByStartedAtDesc(Pageable) and findByTraceIdOrderByStartedAtAsc(String)

## Architecture
- TraceService in io.saira.service.trace package
- TraceController at /api/v1/traces
- Spring Page JSON auto-serialization (content[], totalElements, etc.)
- Frontend: TracesTable + TraceDetailPanel + SpanTreeNode components
- react-query hooks: useTraces(page, size), useTraceDetail(traceId)

## Team
- builder-backend: all Java code
- builder-frontend: shadcn install + all React/TS components
- builder-tests: TraceServiceTest (3 tests) + TraceControllerTest (4 tests)
- validator-final: mvn test + frontend build

## Plan File
specs/langfuse-traces-ui.md
