# Local Startup Guide

## SAIRA (this repo)

### 1. Infrastructure (Podman)
```bash
# Из корня проекта /Users/artemsimeisn/IdeaProjects/saira
podman compose up -d
```
Поднимает:
- **saira-postgres** — PostgreSQL 16 на порту **5432** (user/pass/db: saira/saira/saira)
- **saira-kafka** — Apache Kafka на порту **29092** (KRaft mode, без Zookeeper)
- **saira-kafka-init** — одноразовый контейнер, создаёт топик `fp-saira-spans`

### 2. Backend
```bash
mvn spring-boot:run -Pskip-frontend
```
Запускается на **:8080**. Ожидает PostgreSQL на 5432 и Kafka на 29092.

### 3. Frontend (dev)
```bash
cd frontend && npm run dev
```
Dev server с proxy на :8080.

---

## giga-insurance-java

Проект: `/Users/artemsimeisn/IdeaProjects/sber/giga-insurance-java`

### 1. WireMock (Podman)
```bash
podman start insurance-wiremock
```
Стартует mock-сервер на порту **8001** (уже создан, не нужно заново).

### 2. Backend
```bash
cd /Users/artemsimeisn/IdeaProjects/sber/giga-insurance-java
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
Запускается на **:8081** (задано в application-local.yml).

Профиль `local` включает:
- PostgreSQL на 5432 (БД saira — общая с SAIRA, таблицы раздельные через Liquibase)
- Kafka export спанов на 29092 в топик `fp-saira-spans`
- GigaChat API (credentials в application-local.yml)
- WireMock mock-server на 8001

### Зависимости от saira-инфраструктуры
giga-insurance использует **те же** контейнеры Kafka и PostgreSQL, что и SAIRA.
Поэтому сначала нужно поднять `podman compose up -d` из SAIRA.

---

## Порты (сводка)

| Сервис | Порт |
|--------|------|
| SAIRA backend | 8080 |
| giga-insurance | 8081 |
| PostgreSQL | 5432 |
| Kafka (external) | 29092 |
| WireMock | 8001 |

## Важно
- Ранее контейнеры insurance-kafka/jaeger/otel-collector запускались из giga-insurance docker-compose — теперь Kafka **перенесена в saira docker-compose**. Insurance-контейнеры (кроме wiremock) нужно остановить, если запущены: `podman stop insurance-kafka insurance-otel-collector insurance-jaeger`
- `podman-compose` не установлен, используется `podman compose` (алиас docker-compose → podman compose)
