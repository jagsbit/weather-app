# weather-app

Spring Boot 4 / Java 21 service in `weather-service/` that proxies Open-Meteo (city search and forecast) and serves a static UI from `src/main/resources/static/`.

## Commands (run in `weather-service/`)

- Run: `./mvnw spring-boot:run` (serves the UI and API on port 8080)
- Test: `./mvnw test`

## Workflow

- To build a feature end to end (plan, code, test, review, push), run `/orchestrate <feature description>`. Use `/orchestrate resume <feature>` to continue an interrupted run. If asked for a feature without it, suggest `/orchestrate` rather than running the pipeline agents ad hoc.
- Branch and commit names use `<feature>: <summary>`. Hooks in `.claude/settings.json` block pushes to `main`, force pushes, and pushes of a feature branch without an approved review.
- `state/<feature>/` holds local pipeline state and is gitignored.
- `.mcp.json` is gitignored. Copy `.mcp.json.example` and set `GITHUB_PAT`.
