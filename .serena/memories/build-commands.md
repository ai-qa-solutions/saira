# Build Commands Reference

## Backend only
```bash
mvn compile -Pskip-frontend          # компиляция без фронта
mvn test -Pskip-frontend             # тесты без фронта
mvn spotless:apply -Pskip-frontend   # форматирование Java only
mvn spotless:check -Pskip-frontend   # проверка формата Java only
```

## Full build (backend + frontend)
```bash
mvn clean package                    # полная сборка, JAR с фронтом
mvn spotless:apply                   # форматирование Java + TS/TSX/CSS/JSON
mvn spotless:check                   # проверка всего
```

## Frontend only
```bash
cd frontend && npm run dev           # dev server с proxy на :8080
cd frontend && npm run build         # production build
cd frontend && npm run format        # prettier --write
cd frontend && npm run format:check  # prettier --check
cd frontend && npm run lint          # eslint
```

## Docker
```bash
docker-compose up -d                 # PostgreSQL + Kafka + App
```

## Tests
- Unit: `mvn test` (5 тестов, H2 in-memory)
- Integration: `mvn verify -Pintegration-tests` (Failsafe, **/*IT.java)
- JaCoCo: min 60% line + branch, excludes config/dto/entity/MapperImpl/Application
