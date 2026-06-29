# AGENTS

## Purpose
This repository is a Spring Boot 4 Java 17 application for infant tracking (feeding, pumping, weight, length) plus Home Assistant telemetry and automation.

## Fast Start
- Windows run: `gradlew.bat bootRun`
- Unix run: `./gradlew bootRun`
- Run tests: `gradlew.bat test` or `./gradlew test`
- Build: `gradlew.bat build` or `./gradlew build`

## Core Stack
- Spring Boot 4.0.2, Java 17, Gradle Kotlin DSL
- Spring Web + WebSocket + Data JPA
- SQLite (`./data/app.db`) with Hibernate SQLite dialect
- Quartz scheduler and Spring async executors
- Static frontend pages in `src/main/resources/static`

## Architecture Map
- App bootstrap: `src/main/java/com/example/springboot/Application.java`
- API controllers (feeding/pumping/weight/length/logs): `src/main/java/com/example/springboot/controllers`
- Core business services: `src/main/java/com/example/springboot/services`
- JPA repositories for core entities: `src/main/java/com/example/springboot/persistence`
- Domain models: `src/main/java/com/example/springboot/models`
- Home Assistant integration subsystem: `src/main/java/com/example/springboot/homeassistant`
- Config (async + Quartz): `src/main/java/com/example/springboot/config`

## Key Entry Points
- `src/main/resources/application.properties` for DB, logging, and Home Assistant/automation toggles
- `src/main/java/com/example/springboot/homeassistant/controller/HomeAssistantController.java` for HA-facing API behavior
- `src/main/java/com/example/springboot/homeassistant/automations/BathroomOccupancyAutomation.java` for event-driven automation logic
- `src/main/java/com/example/springboot/config/AsyncConfig.java` for executor/threading behavior
- `src/main/java/com/example/springboot/config/QuartzSchedulerConfig.java` and `src/main/java/com/example/springboot/homeassistant/jobs` for scheduled jobs

## Conventions For Changes
- Follow existing package layering: controller -> service -> repository -> model.
- Keep controllers thin and place business logic in services.
- Reuse repository query patterns for date/time window queries.
- For schema/data changes, add SQL migration scripts under `scripts/` instead of ad hoc runtime changes.
- When changing automation behavior, verify both REST/controller flow and Home Assistant event/websocket flow.

## Project Pitfalls
- Local DB is file-backed SQLite at `./data/app.db`; data persists across runs.
- `spring.jpa.hibernate.ddl-auto=update` can mutate schema automatically; be careful with entity field type/name changes.
- Home Assistant API key is sourced from `HOMEASSISTANT_API_KEY`; avoid hardcoding secrets.
- Logging is verbose for Hibernate SQL and bind values; this is useful for debugging but noisy.

## Testing Guidance
- Current tests are minimal (`src/test/java/com/example/springboot/DemoApplicationTests.java`).
- For non-trivial logic changes, add focused unit/service tests and run `test` before handing off.

## Frontend Notes
- Static dashboards live in:
  - HTML: `src/main/resources/static/html`
  - CSS: `src/main/resources/static/css`
  - JS: `src/main/resources/static/js`
- Keep page-specific CSS/JS naming aligned with existing pattern (`feeding.*`, `pumping.*`, etc.).

## Docs Status
- No README/CONTRIBUTING/ARCHITECTURE docs are present currently.
- Keep this file concise; if project docs are added later, link them here instead of duplicating content.
