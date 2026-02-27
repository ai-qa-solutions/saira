-- shadow_config: shadow testing rules (service -> model -> params -> sampling%)
CREATE TABLE shadow_config (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    service_name    VARCHAR(255) NOT NULL,
    provider_name   VARCHAR(100) NOT NULL,
    model_id        VARCHAR(255) NOT NULL,
    model_params    TEXT,
    sampling_rate   DECIMAL(5,2) NOT NULL DEFAULT 100.00,
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- shadow_result: results of shadow calls against alternative models
CREATE TABLE shadow_result (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_span_id    VARCHAR(32) NOT NULL,
    shadow_config_id  BIGINT NOT NULL,
    model_id          VARCHAR(255) NOT NULL,
    request_body      TEXT,
    response_body     TEXT,
    latency_ms        BIGINT,
    input_tokens      INT,
    output_tokens     INT,
    cost_usd          DECIMAL(12,6),
    status            VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    error_message     TEXT,
    executed_at       TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_shadow_result_span FOREIGN KEY (source_span_id) REFERENCES agent_span(span_id),
    CONSTRAINT fk_shadow_result_config FOREIGN KEY (shadow_config_id) REFERENCES shadow_config(id)
);

-- shadow_evaluation: quality evaluation of shadow results via spring-ai-ragas
CREATE TABLE shadow_evaluation (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shadow_result_id    BIGINT NOT NULL,
    semantic_similarity DECIMAL(5,4),
    correctness_score   DECIMAL(5,4),
    evaluation_detail   TEXT,
    evaluated_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_shadow_eval_result FOREIGN KEY (shadow_result_id) REFERENCES shadow_result(id)
);

-- Indexes for shadow_config
CREATE INDEX idx_shadow_config_service ON shadow_config(service_name);
CREATE INDEX idx_shadow_config_model ON shadow_config(model_id);
CREATE INDEX idx_shadow_config_status ON shadow_config(status);

-- Indexes for shadow_result
CREATE INDEX idx_shadow_result_span ON shadow_result(source_span_id);
CREATE INDEX idx_shadow_result_config ON shadow_result(shadow_config_id);
CREATE INDEX idx_shadow_result_status ON shadow_result(status);
CREATE INDEX idx_shadow_result_executed ON shadow_result(executed_at);
CREATE INDEX idx_shadow_result_model ON shadow_result(model_id);

-- Indexes for shadow_evaluation
CREATE INDEX idx_shadow_eval_result ON shadow_evaluation(shadow_result_id);
