# AGENTS.md

## Project snapshot
- This repository is a **single-module Gradle Spring Boot app** named `tombojava` (`settings.gradle`, `build.gradle`).
- Runtime code includes `src/main/java/it/sdc/tombojava/TombojavaApplication.java`, the CLI entrypoint `src/main/java/it/sdc/tombojava/cli/TombojavaCliRunner.java`, and the Tombola domain package `src/main/java/it/sdc/tombojava/tombola/*`.
- Test surface includes the context smoke test `src/test/java/it/sdc/tombojava/TombojavaApplicationTests.java` plus focused tombola tests in `src/test/java/it/sdc/tombojava/tombola/*Tests.java`.
- Config is minimal and YAML-based: `src/main/resources/application.yaml` (`spring.application.name: tombojava`).
- `README.md` documents the app as a command-line Tombola generator run via `bootRun --args=...`.

## Architecture and boundaries (current state)
- Package root is `it.sdc.tombojava`; `@SpringBootApplication` on `TombojavaApplication` drives component scanning from this root.
- `TombojavaApplication` explicitly runs as a non-web app (`WebApplicationType.NONE`).
- Architecture is currently CLI-driven: `TombojavaCliRunner` orchestrates generation, while `tombola` components handle series generation (`TombolaSeriesGenerator`), PDF writing (`TombolaPdfWriter`), and verification report writing (`TombolaVerificationReportWriter`).
- No explicit external service, DB, messaging, or HTTP integration is configured in code or properties.
- Keep new modules under `src/main/java/it/sdc/tombojava/...` (e.g., `cli`, `tombola`) to stay in scan scope.

## Build and test workflows
- Use the Gradle wrapper in repo root (do not assume a globally installed Gradle).
- Windows test run (verified):
  - `./gradlew.bat test`
- Build artifact:
  - `./gradlew.bat build`
- Run app locally (CLI usage pattern):
  - `./gradlew.bat bootRun --args="--output=out/tombojava.pdf --series=2 --seed=123456789 --max-series-attempts=8000 --report=out/tombojava-report.txt"`
- Supported CLI options in `TombojavaCliRunner`: `--output`, `--series`, `--seed`, `--max-series-attempts`, `--report`.
- Optional JVM debug for Boot run:
  - `./gradlew.bat bootRun --debug-jvm`
- In Spring context smoke tests, disable the CLI runner with `tombojava.cli.enabled=false` (see `TombojavaApplicationTests`).

## Stack and dependency conventions
- Java toolchain is pinned to **Java 21** (`build.gradle` -> `java.toolchain.languageVersion = 21`).
- Spring Boot plugin version is **4.0.5**; dependency management plugin is **1.1.7**.
- Runtime dependencies include `spring-boot-starter` and `org.apache.pdfbox:pdfbox:3.0.4` (for PDF generation).
- Lombok is wired as `compileOnly` + `annotationProcessor` (and mirrored for tests).
- Tests use JUnit Platform via Gradle `useJUnitPlatform()`.
- If changing PDF output behavior or PDFBox version, keep `src/test/java/it/sdc/tombojava/tombola/TombolaPdfWriterTests.java` aligned.

## Agent-specific guidance for safe changes
- Preserve package prefix `it.sdc.tombojava` unless a deliberate refactor is requested.
- Keep new Spring components under that package tree so auto-scan works without extra config.
- If adding framework features (web/data/security), update `build.gradle` explicitly and keep tests aligned with the new slice.
- Keep `TombojavaApplicationTests` with `@SpringBootTest(properties = "tombojava.cli.enabled=false")` for baseline context checks; add focused tests in neighboring packages.
- Do not switch to web app behavior unless explicitly requested; preserve `WebApplicationType.NONE`.
- Treat `TombojavaCliRunner` as the runtime entrypoint; when changing option parsing/defaults, keep `README.md` and related tests consistent.
- `HELP.md` is generic Spring Initializr output; prioritize repository files over `HELP.md` for project truth.

## AI-instruction discovery note
- A current glob scan for common AI instruction files shows `AGENTS.md` and `README.md`; check both before making broad repository changes.

