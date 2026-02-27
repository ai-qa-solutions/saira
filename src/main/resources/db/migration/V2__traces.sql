-- agent_trace: one row per unique trace_id
CREATE TABLE agent_trace (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trace_id        VARCHAR(64) NOT NULL UNIQUE,
    service_name    VARCHAR(255) NOT NULL,
    fp_id           VARCHAR(255),
    fp_module_id    VARCHAR(255),
    started_at      TIMESTAMP NOT NULL,
    ended_at        TIMESTAMP,
    span_count      INTEGER NOT NULL DEFAULT 0,
    status          VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- agent_span: one row per HTTP client span with structured fields
CREATE TABLE agent_span (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trace_id        VARCHAR(64) NOT NULL,
    span_id         VARCHAR(32) NOT NULL UNIQUE,
    parent_span_id  VARCHAR(32),
    operation_name  VARCHAR(500) NOT NULL,
    span_kind       VARCHAR(32) NOT NULL DEFAULT 'CLIENT',
    started_at      TIMESTAMP NOT NULL,
    duration_micros BIGINT NOT NULL,
    status_code     VARCHAR(32),
    http_url        VARCHAR(2000),
    http_method     VARCHAR(16),
    http_status     VARCHAR(16),
    client_name     VARCHAR(500),
    request_body    TEXT,
    response_body   TEXT,
    outcome         VARCHAR(32),
    CONSTRAINT fk_span_trace FOREIGN KEY (trace_id) REFERENCES agent_trace(trace_id)
);

-- Indexes
CREATE INDEX idx_trace_service ON agent_trace(service_name);
CREATE INDEX idx_trace_started ON agent_trace(started_at);
CREATE INDEX idx_trace_fp_id ON agent_trace(fp_id);
CREATE INDEX idx_trace_created ON agent_trace(created_at);
CREATE INDEX idx_span_trace_id ON agent_span(trace_id);
CREATE INDEX idx_span_operation ON agent_span(operation_name);
CREATE INDEX idx_span_http_url ON agent_span(http_url);
CREATE INDEX idx_span_started ON agent_span(started_at);
