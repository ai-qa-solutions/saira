<div align="center">

<img src="docs/assets/saira-header.svg" width="100%" alt="S[AI]RA — System for Agentic Intelligence Review & Analysis">

<br>

<a href="#what-is-saira"><b>About</b></a> &nbsp;&middot;&nbsp;
<a href="#architecture"><b>Architecture</b></a> &nbsp;&middot;&nbsp;
<a href="#evaluation-metrics"><b>Metrics</b></a> &nbsp;&middot;&nbsp;
<a href="#shadow-testing"><b>Shadow Testing</b></a>

<br><br>

<img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21">
<img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot">
<img src="https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react&logoColor=black" alt="React 19">
<img src="https://img.shields.io/badge/Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white" alt="Kafka">
<img src="https://img.shields.io/badge/OpenTelemetry-000?style=for-the-badge&logo=opentelemetry&logoColor=white" alt="OTel">
<img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL">

<br>

<img src="https://img.shields.io/badge/status-active_development-yellow?style=flat-square" alt="Status: Active Development">
<a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green?style=flat-square" alt="License: MIT"></a>

<a href="README.md"><img src="https://img.shields.io/badge/lang-EN-blue?style=flat-square" alt="English"></a>
<a href="README.ru.md"><img src="https://img.shields.io/badge/lang-RU-blue?style=flat-square" alt="Русский"></a>

</div>

---

## What is SAIRA?

SAIRA is an automated quality assessment platform for AI agents in production. It collects telemetry from agent execution chains (LLM calls, tool calls, full sessions), analyzes them and evaluates correctness, efficiency and cost — without manual review.

### Core Capabilities

- **Ingestion** — accepts data via REST API and Apache Kafka in OpenTelemetry format (GenAI semantic conventions)
- **Agent Trace Analysis** — reconstructs the full agent execution chain from spans: LLM calls, tool calls, intermediate steps
- **LLM as Judge** — automated quality assessment of LLM responses and tool calls using [spring-ai-ragas](https://github.com/ai-qa-solutions/spring-ai-ragas)
- **Shadow Testing** — duplicates agent requests to alternative models and tool versions for A/B comparison without affecting production
- **Evaluation Dashboard** — visual panel with metrics, trends and drill-down by agent sessions

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.5.11, Spring WebMVC |
| Data | PostgreSQL 16, Spring Data JPA, Flyway |
| Messaging | Apache Kafka, Spring Kafka |
| Evaluation | spring-ai-ragas 0.3.1 (LLM as Judge, NLP metrics, agent metrics) |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4, shadcn/ui |
| Infrastructure | Docker / Podman, docker-compose |

## Architecture

```
                    ┌─────────────────┐
                    │   AI Agents     │
                    │  (Production)   │
                    └────────┬────────┘
                             │ OTel spans (gen_ai.*, saira.*)
                    ┌────────┴────────┐
              ┌─────┤   Ingestion     ├─────┐
              │     └─────────────────┘     │
         REST API                      Kafka Consumer
              │                             │
              └──────────┬──────────────────┘
                         │
                ┌────────▼────────┐
                │  Trace Builder  │
                │  (Agent Chain   │
                │   Reconstruction)│
                └────────┬────────┘
                         │
           ┌─────────────┼─────────────┐
           │             │             │
    ┌──────▼──────┐ ┌────▼─────┐ ┌────▼──────┐
    │ LLM as Judge│ │  Shadow  │ │   NLP     │
    │ (spring-ai- │ │  Calls   │ │  Metrics  │
    │  ragas)     │ │          │ │           │
    └──────┬──────┘ └────┬─────┘ └────┬──────┘
           │             │             │
           └─────────────┼─────────────┘
                         │
                ┌────────▼────────┐
                │   PostgreSQL    │
                │   (Results &    │
                │    Analytics)   │
                └────────┬────────┘
                         │
                ┌────────▼────────┐
                │   Dashboard     │
                │  (React SPA)    │
                └─────────────────┘
```

## Evaluation Metrics

SAIRA uses [spring-ai-ragas](https://github.com/ai-qa-solutions/spring-ai-ragas) for comprehensive evaluation:

| Category | Metrics | LLM Required |
|----------|---------|:---:|
| **General Purpose** | AspectCritic, SimpleCriteriaScore, RubricsScore | Yes |
| **Agent** | AgentGoalAccuracy, ToolCallAccuracy, TopicAdherence | Partial |
| **Retrieval** | Faithfulness, ContextPrecision, ContextRecall, ResponseRelevancy | Yes |
| **Response Quality** | AnswerCorrectness, FactualCorrectness, SemanticSimilarity | Partial |
| **NLP (Deterministic)** | BLEU, ROUGE, chrF, StringSimilarity | No |

### With vs Without References

| Mode | Use Case | Metrics |
|------|----------|---------|
| **With references** | Regression tests, synthetic monitoring | Full set (~8 LLM calls) |
| **Without references** | Production traffic sampling | Subset (~6 LLM calls + embeddings) |

## Shadow Testing

Shadow testing allows evaluating alternative models and configurations without affecting production:

1. SAIRA intercepts agent requests from telemetry
2. Duplicates requests to selected alternative models / tool versions
3. Evaluates and compares results across all metrics
4. Displays A/B comparison in the dashboard

**Result**: zero-cost verification of new model and tool versions before migration.

## License

[MIT](LICENSE)

---

Built with [spring-ai-ragas](https://github.com/ai-qa-solutions/spring-ai-ragas) for AI evaluation.
