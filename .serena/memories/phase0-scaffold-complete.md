# Phase 0: Project Scaffold — Выполнен

## Пакет и координаты
- **Maven groupId**: `io.saira`
- **Java package**: `io.saira`
- **artifactId**: `saira`, version `0.1.0-SNAPSHOT`
- **GitHub**: https://github.com/ai-qa-solutions/saira

## Что создано

### Backend (Java 21 + Spring Boot 3.5.11)
- `pom.xml` — Maven с parent spring-boot-starter-parent:3.5.11
- Dependencies: web, data-jpa, validation, cache, actuator, security, kafka, spring-ai-ragas:0.3.1, postgresql, flyway, mapstruct:1.6.3, lombok, caffeine, springdoc:2.7.0, h2(test)
- Plugins: compiler(3.13.0), frontend-maven-plugin(1.15.1), resources, spotless(2.43.0), jacoco(0.8.12), surefire(3.5.2), spring-boot
- Profiles: `skip-frontend`, `integration-tests`

### Java Sources (package io.saira)
- `io.saira.SairaApplication` — @SpringBootApplication + @EnableCaching
- `io.saira.config.WebConfig` — static resource handlers for SPA
- `io.saira.config.SecurityConfig` — permitAll, CSRF disabled, stateless
- `io.saira.config.CacheConfig` — Caffeine 5min TTL, 500 max
- `io.saira.controller.SpaController` — forwards SPA routes to index.html
- `io.saira.controller.api.HealthCheckController` — GET /api/v1/health

### Spring Config
- `application.yml` — datasource(PostgreSQL), JPA(validate), Flyway, Kafka, Actuator, Springdoc + dev/prod profiles
- `application-test.yml` — H2 in-memory, MODE=PostgreSQL, Kafka auto-config excluded
- `application-local.yml` — placeholder for secrets (gitignored)

### Flyway
- `V1__init.sql` — app_settings table (setting_key/setting_value — NOT key/value, reserved in H2)

### Tests (5 total, all pass)
- `SairaApplicationTest` — context loads (@ActiveProfiles("test"))
- `HealthCheckControllerTest` — @WebMvcTest + @Import(SecurityConfig.class)
- `SpaControllerTest` — forwarded URL is `/index.html` (with leading slash)

### Frontend (React 19 + TypeScript + Vite 7)
- shadcn/ui initialized with oklch CSS variables
- react-router-dom v7 with createBrowserRouter + lazy routes
- @tanstack/react-query provider
- Layout with sidebar navigation
- 5 page stubs: Dashboard, Traces, Evaluations, Settings, Shadow Tests
- API client in `src/api/client.ts`
- Path aliases: `@/` → `./src/`

### Docker
- `Dockerfile` — multi-stage (maven → jre-alpine), non-root user
- `docker-compose.yml` — PostgreSQL 16 + Kafka KRaft + App
- `.dockerignore`

## Spotless Configuration
- **Java**: Palantir format, removeUnusedImports, importOrder(java,javax,org,com,), formatAnnotations
- **TypeScript/TSX**: Prettier 3.4.2 — double quotes, semicolons, 2 spaces, trailing commas, printWidth=80
- **CSS**: Prettier 3.4.2
- **JSON**: Prettier 3.4.2 (frontend/src only)
- **Markdown**: НЕ форматируется (flexmark ломал .md файлы — убрали)

## Важные решения и баги
- H2 не поддерживает `key`/`value` как имена колонок (reserved words) → `setting_key`/`setting_value`
- H2 URL: `MODE=PostgreSQL` для совместимости с Flyway
- SPA forwarded URL = `/index.html` (со слешем)
- Kafka auto-config исключён в test профиле через `spring.autoconfigure.exclude`
- @WebMvcTest контроллеров требует `@Import(SecurityConfig.class)` иначе 401
