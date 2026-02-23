# CLAUDE.md - S[AI]RA Development Guide

## Project Overview

**SAIRA** (System for Agentic Intelligence Review & Analysis) — платформа для автоматической оценки качества работы AI-агентов. Анализирует цепочки вызовов LLM и инструментов, оценивает их корректность, эффективность и стоимость. Позволяет проводить shadow-тестирование новых моделей и версий тулов без воздействия на продакшн.

## Tech Stack

### Backend
- **Java 21** + **Spring Boot 3.5.11** (WebMVC)
- **Spring Data JPA** + **Flyway** миграции
- **PostgreSQL 16** (prod) / **H2 in-memory** (unit tests)
- **Spring Kafka** — потребление OTel-proto данных (GenAI + SAIRA нотации)
- **spring-ai-ragas** (`io.github.ai-qa-solutions:spring-ai-ragas-spring-boot-starter:0.3.1`) — LLM as Judge, NLP-метрики, agent-метрики
- **MapStruct** + **Lombok** — маппинг и boilerplate
- **Caffeine** — in-memory кэш
- **Spotless** (Palantir style) — форматирование кода
- **JaCoCo** — покрытие тестов (минимум 60% line/branch)

### Frontend
- **React 19** + **TypeScript 5.9** + **Vite 7**
- **Tailwind CSS 4** + **shadcn/ui**
- **@tanstack/react-query** — серверный стейт
- **react-router-dom 7** — маршрутизация
- **recharts** — графики и дашборды

### Infrastructure
- **Podman** / **Docker** — контейнеры
- **docker-compose** — PostgreSQL + Kafka + Zookeeper + App
- **Nginx** — reverse proxy (prod)

## Project Structure

```
saira/
├── pom.xml                          # Maven root (backend + frontend build)
├── Dockerfile                       # Multi-stage сборка
├── docker-compose.yml               # PostgreSQL + Kafka + App
├── src/
│   ├── main/
│   │   ├── java/com/saira/
│   │   │   ├── SairaApplication.java
│   │   │   ├── config/              # Spring конфигурация
│   │   │   ├── controller/          # REST API контроллеры
│   │   │   │   └── api/             # Public API
│   │   │   ├── service/             # Бизнес-логика
│   │   │   │   ├── ingest/          # Потребление данных (REST + Kafka)
│   │   │   │   ├── analysis/        # Анализ цепочек агентов
│   │   │   │   ├── evaluation/      # Оценка (spring-ai-ragas)
│   │   │   │   └── shadow/          # Shadow-вызовы
│   │   │   ├── entity/              # JPA сущности
│   │   │   ├── repository/          # Spring Data репозитории
│   │   │   ├── dto/                 # DTO объекты
│   │   │   ├── kafka/               # Kafka consumers и конфиги
│   │   │   └── util/                # Утилиты
│   │   └── resources/
│   │       ├── application.yml      # Основная конфигурация
│   │       ├── application-local.yml # Локальные секреты (gitignored)
│   │       ├── application-prod.yml  # Продакшн профиль
│   │       ├── db/migration/         # Flyway миграции
│   │       └── static/               # Frontend dist (генерируется)
│   └── test/
│       ├── java/com/saira/
│       │   ├── controller/           # Тесты контроллеров
│       │   ├── service/              # Тесты сервисов
│       │   └── kafka/                # Тесты Kafka consumer'ов
│       └── resources/
│           └── application-test.yml  # H2 in-memory конфиг
├── frontend/
│   ├── src/
│   │   ├── api/                     # API клиенты
│   │   ├── components/              # UI компоненты (shadcn)
│   │   ├── pages/                   # Страницы приложения
│   │   ├── context/                 # React контексты
│   │   ├── hooks/                   # Custom hooks
│   │   ├── types/                   # TypeScript типы
│   │   ├── lib/                     # shadcn утилиты
│   │   ├── App.tsx                  # Маршрутизация
│   │   ├── main.tsx                 # Точка входа
│   │   └── index.css                # Глобальные стили + Tailwind
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── tsconfig.app.json
│   ├── tsconfig.node.json
│   ├── eslint.config.js
│   └── components.json              # shadcn конфиг
└── specs/                           # Спецификации и планы
```

## Build & Run

```bash
# Полная сборка (backend + frontend)
mvn clean package

# Только backend (пропуск frontend)
mvn spring-boot:run -Pskip-frontend

# Только frontend (dev server с proxy на :8080)
cd frontend && npm run dev

# Запуск тестов
mvn test                              # Unit тесты (H2)
mvn verify -Pintegration-tests        # Integration тесты

# Форматирование кода
mvn spotless:apply

# Docker
docker-compose up -d                  # PostgreSQL + Kafka + App
podman-compose up -d                  # Альтернатива с Podman
```

## Profiles

| Профиль | Описание | БД | Kafka |
|---------|----------|-----|-------|
| `default` | Разработка | PostgreSQL localhost:5432 | localhost:9092 |
| `local` | Локальные секреты | PostgreSQL localhost:5432 | localhost:9092 |
| `test` | Тестирование | H2 in-memory | Embedded Kafka |
| `prod` | Продакшн | PostgreSQL (env vars) | Kafka (env vars) |

## Key Conventions

### Backend
- Пакет: `com.saira`
- REST API prefix: `/api/v1/`
- Миграции: `V{N}__{description}.sql` в `resources/db/migration/`
- Тесты unit: `*Test.java` (Surefire)
- Тесты integration: `*IT.java` (Failsafe, профиль `integration-tests`)
- Форматирование: Spotless Palantir Java style
- Не используем `var` — явные типы
- DTO маппинг через MapStruct
- Каждый контроллер покрыт тестами
- Сервисы — @Service, без бизнес-логики в контроллерах

### Frontend
- TypeScript strict mode
- Компоненты: функциональные, с хуками
- Стили: Tailwind CSS utility-first + shadcn/ui компоненты
- API вызовы: через `@tanstack/react-query`
- Формы: `react-hook-form`
- Роутинг: `react-router-dom` с ленивой загрузкой

### Git
- Ветки: `feature/`, `fix/`, `refactor/`
- Коммиты: осмысленные, краткие, на английском
- Не коммитить `application-local.yml`, `.env`, credentials

## Architecture Notes

### Data Ingestion
- **REST endpoint** (`POST /api/v1/ingest`) — приём OTel-proto данных напрямую
- **Kafka consumer** — топик с OTel-proto данными (GenAI + SAIRA семантические конвенции)
- Данные приходят в формате OpenTelemetry spans с атрибутами `gen_ai.*` и `saira.*`

### Agent Trace Analysis
- Фильтрация spans: только агентные цепочки (LLM calls + tool calls)
- Построение полного дерева сессии агента из trace/span ID
- Хранение нормализованных данных в PostgreSQL

### Evaluation (spring-ai-ragas)
- **LLM as Judge** — AspectCritic, SimpleCriteriaScore, RubricsScore
- **Agent Metrics** — AgentGoalAccuracy, ToolCallAccuracy, TopicAdherence
- **NLP Metrics** — BLEU, ROUGE, chrF, StringSimilarity
- **Retrieval Metrics** — Faithfulness, ContextPrecision, ContextRecall
- Поддержка оценки с и без референсов

### Shadow Calls
- SAIRA дублирует запросы агента к другим моделям/версиям тулов
- Сравнение результатов без влияния на продакшн
- Zero-cost тестирование новых версий моделей

## Environment Variables

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=saira
DB_USERNAME=saira
DB_PASSWORD=saira

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_GROUP_ID=saira-consumer
KAFKA_TOPIC_OTEL=otel-traces

# Spring
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8080

# AI Providers (для spring-ai-ragas и shadow calls)
SPRING_AI_OPENAI_API_KEY=
SPRING_AI_ANTHROPIC_API_KEY=
```

## Reference Projects
- **tutor-library** — референсный проект с аналогичным стеком (Spring Boot 3.5.11 + React 19 + Vite + Tailwind)
- **spring-ai-ragas** — библиотека оценки (`https://github.com/ai-qa-solutions/spring-ai-ragas`)
