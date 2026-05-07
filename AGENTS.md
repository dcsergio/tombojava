# AGENTS.md

## Project snapshot
- This repository is a **single-module Gradle Spring Boot app** named `tombojava` (`settings.gradle`, `build.gradle`).
- Runtime code includes `src/main/java/it/sdc/tombojava/TombojavaApplication.java`, the web package `src/main/java/it/sdc/tombojava/web/*`, the background job package `src/main/java/it/sdc/tombojava/job/*`, the optional CLI entrypoint `src/main/java/it/sdc/tombojava/cli/TombojavaCliRunner.java`, and the Tombola domain package `src/main/java/it/sdc/tombojava/tombola/*`.
- Test surface includes the context smoke test `src/test/java/it/sdc/tombojava/TombojavaApplicationTests.java`, focused tombola tests in `src/test/java/it/sdc/tombojava/tombola/*Tests.java`, job tests in `src/test/java/it/sdc/tombojava/job/*Tests.java`, and web request validation tests in `src/test/java/it/sdc/tombojava/web/*Tests.java`.
- Config is YAML-based in `src/main/resources/application.yaml` and currently includes `spring.application.name`, `tombojava.cli.enabled`, `tombojava.output-dir`, and `tombojava.max-series-attempts-default`.
- `README.md` documents the app primarily as a web UI served at `http://localhost:8080`, with optional CLI mode and Docker/Render notes.

## Architecture and boundaries (current state)
- Package root is `it.sdc.tombojava`; `@SpringBootApplication` on `TombojavaApplication` drives component scanning from this root.
- `TombojavaApplication` now uses the default Spring Boot startup path and runs as a web app.
- Architecture is now web-first: `HomeController` serves `templates/index.html`, `GenerationJobController` exposes `/api/jobs` endpoints, `GenerationJobService` manages in-memory async generation/download/verification state, and `tombola` components still handle series generation (`TombolaSeriesGenerator`), PDF writing (`TombolaPdfWriter`), and verification report writing (`TombolaVerificationReportWriter`).
- There is still no explicit DB or messaging integration, but HTTP/UI integration is now part of the app via Spring MVC, Thymeleaf templates, and `src/main/resources/static/js/job-polling.js`.
- Keep new modules under `src/main/java/it/sdc/tombojava/...` (e.g., `web`, `job`, `cli`, `tombola`) to stay in scan scope.

## Build and test workflows
- Use the Gradle wrapper in repo root (do not assume a globally installed Gradle).
- Windows test run (verified):
  - `./gradlew.bat test`
- Build artifact:
  - `./gradlew.bat build`
- Run app locally (web default):
  - `./gradlew.bat bootRun`
  - then open `http://localhost:8080`
- Optional CLI mode:
  - `./gradlew.bat bootRun --args="--tombojava.cli.enabled=true --output=out/tombojava.pdf --series=2 --seed=123456789 --max-series-attempts=8000 --report=out/tombojava-report.txt"`
- Supported CLI options in `TombojavaCliRunner`: `--output`, `--series`, `--seed`, `--max-series-attempts`, `--report`.
- Optional JVM debug for Boot run:
  - `./gradlew.bat bootRun --debug-jvm`
- Container workflow is documented and backed by `Dockerfile` in repo root:
  - `docker build -t tombojava .`
  - `docker run --rm -p 8080:8080 -e PORT=8080 tombojava`
- In Spring context smoke tests, disable the CLI runner with `tombojava.cli.enabled=false` (see `TombojavaApplicationTests`).

## Stack and dependency conventions
- Java toolchain is pinned to **Java 21** (`build.gradle` -> `java.toolchain.languageVersion = 21`).
- Spring Boot plugin version is **4.0.5**; dependency management plugin is **1.1.7**.
- Runtime dependencies include `spring-boot-starter`, `spring-boot-starter-web`, `spring-boot-starter-thymeleaf`, and `org.apache.pdfbox:pdfbox:3.0.4` (for PDF generation).
- Lombok is wired as `compileOnly` + `annotationProcessor` (and mirrored for tests).
- Tests use JUnit Platform via Gradle `useJUnitPlatform()`.
- If changing PDF output behavior or PDFBox version, keep `src/test/java/it/sdc/tombojava/tombola/TombolaPdfWriterTests.java` aligned.

## Agent-specific guidance for safe changes
- Preserve package prefix `it.sdc.tombojava` unless a deliberate refactor is requested.
- Keep new Spring components under that package tree so auto-scan works without extra config.
- If adding framework features (web/data/security), update `build.gradle` explicitly and keep tests aligned with the new slice.
- Keep `TombojavaApplicationTests` with `@SpringBootTest(properties = "tombojava.cli.enabled=false")` for baseline context checks; add focused tests in neighboring packages.
- Do not remove the current web-app behavior or reintroduce `WebApplicationType.NONE` unless explicitly requested.
- Treat `HomeController` + `GenerationJobController` as the primary runtime entrypoints and `TombojavaCliRunner` as an optional mode; when changing `/api/jobs` payloads, polling/download/verify flow, or CLI option parsing/defaults, keep `README.md`, `src/main/resources/templates/index.html`, `src/main/resources/static/js/job-polling.js`, and related tests consistent.
- `GenerationJobService` keeps job state and generated `TombolaSeries` in memory for later download/verification; changes to job lifecycle, persistence, or status fields affect both controller responses and the browser flow.
- Web request validation currently lives in `StartGenerationRequest.validate()` and `VerifySeriesRequest.validate()`; keep `src/test/java/it/sdc/tombojava/web/*Tests.java` aligned if validation rules move or expand.
- `HELP.md` is generic Spring Initializr output; prioritize repository files over `HELP.md` for project truth.

## AI-instruction discovery note
- A current glob scan for common AI instruction files shows `AGENTS.md` and `README.md`; check both before making broad repository changes.

