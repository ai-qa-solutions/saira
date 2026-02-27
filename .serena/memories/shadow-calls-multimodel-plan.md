# Shadow Calls Multi-Model Plan

## Key Decisions
- Providers configured via YAML (like spring-ai-ragas MultiProviderAutoConfiguration)
- Shadow rules managed via Settings UI (service → model → params → sampling %)
- Model lists fetched via API call to providers (GET /v1/models)
- Dynamic ChatClient creation using mutate() pattern for OpenRouter, bean auto-detection for GigaChat
- Three shadow modes: real-time interception at ingest, retrospective replay, manual per-span
- Results: response + latency + tokens + cost + Semantic Similarity + Correctness (spring-ai-ragas)
- Chat View: shadow responses shown after assistant messages with Compare button

## Architecture Patterns (from spring-ai-ragas)
- **mutate() pattern**: OpenAiApi.mutate().baseUrl().apiKey().build() for OpenRouter
- **Bean auto-detection**: Map<String, ChatModel> + class name filtering for GigaChat
- **ShadowChatClientRegistry**: ConcurrentHashMap<String, ChatClient> thread-safe registry
- **Async execution**: Separate thread pool (shadowExecutor) to not block ingestion

## DB Tables (V3 Migration)
- shadow_config (service_name, provider_name, model_id, model_params JSONB, sampling_rate, status)
- shadow_result (source_span_id FK, shadow_config_id FK, model_id, request/response_body, latency, tokens, cost)
- shadow_evaluation (shadow_result_id FK, semantic_similarity, correctness_score, evaluation_detail JSONB)

## Maven Dependencies
- org.springframework.ai:spring-ai-openai-spring-boot-starter (mutate pattern)
- chat.giga.springai:spring-ai-starter-model-gigachat:0.6.0
- Spring AI BOM 1.0.0

## Plan File
- specs/shadow-calls-multimodel.md
