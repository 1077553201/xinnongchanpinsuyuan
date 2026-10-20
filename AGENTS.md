# Repository Guidelines

## Project Structure & Module Organization
This agricultural traceability system combines Vue 3, a Java 11 Spring Boot backend, MySQL, MQTT, and FISCO BCOS.

- `frontend/src/`: pages in `views/`, reusable components, API clients, types, routing, and shared CSS. `frontend/mock/` provides preview data.
- `backend/src/main/java/org/nongchanpin/`: controllers, services, MyBatis mappers, models, blockchain adapters, and IoT ingestion. Tests mirror packages under `backend/src/test/java/`.
- `contract/`: Solidity source, Java wrapper, integration contract, and deployment records; backend contract artifacts live under `backend/src/main/resources/`.
- `database/`: schema and seed SQL. `deploy/emqx/`: local broker setup. `docs/`: project goals, implementation prompts, and reference screenshots.

## Build, Test, and Development Commands
Run commands from the indicated directory:

- `frontend/`: `npm install`, then `npm run dev` starts Vite on port 5173, proxying `/api` to backend port 8080.
- `frontend/`: run `npm run mock` and `npm run dev:mock` in separate terminals for preview data on port 8090.
- `frontend/`: `npm run build` checks TypeScript and produces `dist/`; `npm run type-check` checks types only.
- `backend/`: `mvn test`, `mvn package`, and `mvn spring-boot:run` test, package, and start the service. Windows wrapper equivalents use `.\mvnw.cmd`; restore the missing `.mvn/wrapper/` files before relying on them.

## Coding Style & Naming Conventions
Use four-space Java indentation and two-space Vue/TypeScript/CSS indentation. Java classes and Vue components use PascalCase; methods and variables use camelCase. Follow existing suffixes such as `Controller`, `Service`, `Mapper`, and `Request`. Use Vue Composition API with `<script setup lang="ts">`, single-quoted TypeScript strings, and no statement semicolons. Reuse CSS tokens in `frontend/src/styles/`. No dedicated formatter or linter is configured.

## Testing Guidelines
Backend tests use JUnit 5 and Mockito; name classes `*Test.java` and methods after expected behavior. Frontend state tests use Node's built-in runner: run `npm test` in `frontend/`, with tests named `tests/*.test.mjs`. No coverage threshold is configured. For UI changes, run the frontend build and inspect desktop/mobile layouts; distinguish mock checks from live integration validation.

## Commit & Pull Request Guidelines
Existing commits use type prefixes (`docs:`, `chore:`) with concise Chinese summaries. Continue the `type: summary` pattern. PRs should describe behavior changes, link relevant issues, report validation and configuration requirements, and include screenshots for UI changes.

## Security & Data Boundaries
Keep secrets in environment variables or ignored `application-local.properties`; never commit private keys or certificates. Store business details and raw sensor data off-chain; preserve canonical hashes and truthful chain statuses, including `CHAIN_DISABLED`. Keep ABI, bytecode, wrappers, and integration documentation synchronized when contracts change.
