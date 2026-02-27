# Shadow Model Registry Refactor

## Decision
Replace `ShadowChatClientRegistry` (modelId→ChatClient) with `ShadowModelRegistry` (providerId→ChatModel).
Use `ChatModel.call(new Prompt(messages, OpenAiChatOptions))` for per-call option overrides.

## Why
- Same model with different params (temp=0.0 vs temp=0.7) overwrote each other in modelId-keyed map
- GigaChat's `createGenericClient()` ignored params entirely
- ChatClient wrapper added unnecessary complexity — Spring AI ChatModel supports per-call options natively

## Key API
- `ShadowModelRegistry.registerModel(providerId, ChatModel)` — one model per provider
- `ShadowModelRegistry.getModel(providerId)` → `Optional<ChatModel>`
- `chatModel.call(new Prompt(messages, OpenAiChatOptions.builder().model(modelId).temperature(temp).build()))`

## Files Changed
- NEW: `ShadowModelRegistry.java` (simple provider→ChatModel map)
- DELETED: `ShadowChatClientRegistry.java`
- MODIFIED: `ShadowAutoConfiguration.java` (simplified, no restorer bean)
- MODIFIED: `ShadowConfigService.java` (pure CRUD, no registry)
- MODIFIED: `ShadowExecutionService.java` (ChatModel.call with per-call options)

## Plan File
`specs/shadow-model-registry-refactor.md`
