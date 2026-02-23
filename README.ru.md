<div align="center">

<img src="docs/assets/saira-header.svg" width="100%" alt="S[AI]RA — System for Agentic Intelligence Review & Analysis">

<br>

<a href="#что-такое-saira"><b>О проекте</b></a> &nbsp;&middot;&nbsp;
<a href="#архитектура"><b>Архитектура</b></a> &nbsp;&middot;&nbsp;
<a href="#метрики-оценки"><b>Метрики</b></a> &nbsp;&middot;&nbsp;
<a href="#shadow-тестирование"><b>Shadow Testing</b></a>

<br><br>

<img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21">
<img src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot">
<img src="https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react&logoColor=black" alt="React 19">
<img src="https://img.shields.io/badge/Kafka-231F20?style=for-the-badge&logo=apachekafka&logoColor=white" alt="Kafka">
<img src="https://img.shields.io/badge/OpenTelemetry-000?style=for-the-badge&logo=opentelemetry&logoColor=white" alt="OTel">
<img src="https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL">

<br>

<img src="https://img.shields.io/badge/статус-активная_разработка-yellow?style=flat-square" alt="Статус: Активная разработка">
<a href="LICENSE"><img src="https://img.shields.io/badge/лицензия-MIT-green?style=flat-square" alt="Лицензия: MIT"></a>

<a href="README.ru.md"><img src="https://img.shields.io/badge/lang-RU-blue?style=flat-square" alt="Русский"></a>
<a href="README.md"><img src="https://img.shields.io/badge/lang-EN-blue?style=flat-square" alt="English"></a>

</div>

---

## Что такое SAIRA?

SAIRA — платформа для автоматической оценки качества работы AI-агентов в продакшне. Собирает телеметрию агентных цепочек (вызовы LLM, вызовы инструментов, полные сессии), анализирует их и оценивает корректность, эффективность и стоимость — без ручного ревью.

### Ключевые возможности

- **Ingestion** — приём данных по REST API и Apache Kafka в формате OpenTelemetry (GenAI semantic conventions)
- **Agent Trace Analysis** — восстановление полной цепочки работы агента из spans: вызовы LLM, вызовы инструментов, промежуточные шаги
- **LLM as Judge** — автоматическая оценка качества ответов LLM и вызовов инструментов на базе [spring-ai-ragas](https://github.com/ai-qa-solutions/spring-ai-ragas)
- **Shadow Testing** — дублирование запросов агента к альтернативным моделям и версиям тулов для A/B сравнения без влияния на продакшн
- **Evaluation Dashboard** — визуальная панель с метриками, трендами и drill-down по сессиям агентов

## Стек технологий

| Слой | Технология |
|------|-----------|
| Backend | Java 21, Spring Boot 3.5.11, Spring WebMVC |
| Данные | PostgreSQL 16, Spring Data JPA, Flyway |
| Messaging | Apache Kafka, Spring Kafka |
| Оценка | spring-ai-ragas 0.3.1 (LLM as Judge, NLP-метрики, агентные метрики) |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4, shadcn/ui |
| Инфраструктура | Docker / Podman, docker-compose |

## Архитектура

```
                    ┌─────────────────┐
                    │   AI-агенты     │
                    │  (Продакшн)     │
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
                │  (Восстановление│
                │   цепочки)      │
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
                │   (Результаты   │
                │    и аналитика) │
                └────────┬────────┘
                         │
                ┌────────▼────────┐
                │   Dashboard     │
                │  (React SPA)    │
                └─────────────────┘
```

## Метрики оценки

SAIRA использует [spring-ai-ragas](https://github.com/ai-qa-solutions/spring-ai-ragas) для комплексной оценки:

| Категория | Метрики | Требуется LLM |
|-----------|---------|:---:|
| **Общего назначения** | AspectCritic, SimpleCriteriaScore, RubricsScore | Да |
| **Агентные** | AgentGoalAccuracy, ToolCallAccuracy, TopicAdherence | Частично |
| **Retrieval** | Faithfulness, ContextPrecision, ContextRecall, ResponseRelevancy | Да |
| **Качество ответов** | AnswerCorrectness, FactualCorrectness, SemanticSimilarity | Частично |
| **NLP (детерминированные)** | BLEU, ROUGE, chrF, StringSimilarity | Нет |

### С референсами и без

| Режим | Сценарий | Метрики |
|-------|----------|---------|
| **С референсами** | Регрессионные тесты, синтетический мониторинг | Полный набор (~8 вызовов LLM) |
| **Без референсов** | Сэмплирование продакшн-трафика | Подмножество (~6 вызовов LLM + embeddings) |

## Shadow-тестирование

Shadow testing позволяет оценивать альтернативные модели и конфигурации без влияния на продакшн:

1. SAIRA перехватывает запросы агента из телеметрии
2. Дублирует запросы к выбранным альтернативным моделям / версиям тулов
3. Оценивает и сравнивает результаты по всем метрикам
4. Отображает A/B сравнение в дашборде

**Результат**: zero-cost проверка новых версий моделей и тулов перед миграцией.

## Лицензия

[MIT](LICENSE)

---

Построено с использованием [spring-ai-ragas](https://github.com/ai-qa-solutions/spring-ai-ragas) для оценки AI.
