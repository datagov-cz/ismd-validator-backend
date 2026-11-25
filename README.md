# ISMD Validator Backend

Spring Boot service (Java 17) that converts vocabularies (Archi XML, EA XMI, Excel, SSP) into OFN Slovníky
JSON-LD / Turtle and validates them with SHACL rules, plus optional corpus-aware (global) checks.

- Module: [`ismd-backend-validator/`](ismd-backend-validator/)
- Validation rules: [`ismd-backend-validator/VALIDATION-RULES.md`](ismd-backend-validator/VALIDATION-RULES.md)
- Callers: the validator frontend (browser, subject to CORS) and `ismd-tool-backend` (server-to-server).
- Internal service: no authentication or rate limiting. Do not expose it publicly.

## Run

```bash
# tests
cd ismd-backend-validator && ./mvnw test

# local, from source (port 8082, profile localhost)
cd ismd-backend-validator && ./mvnw spring-boot:run -Dspring-boot.run.profiles=localhost

# docker (host 8082 -> container 8080)
docker compose -f docker-compose.yml -f docker-compose.build.yml up --build
```

Swagger UI: `/validujeme/swagger-ui.html` · OpenAPI: `/validujeme/v3/api-docs`

## Configuration (environment variables)

| Variable | Default | Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `production` (Dockerfile) | Must be one of `production`, `stage`, `localhost`, `test`. Any other value fails startup. |
| `CORS_ALLOWED_ORIGINS` | empty | Comma-separated exact frontend origins, e.g. `https://validator.example.gov.cz`. **Required in production/stage**: if empty, all browser cross-origin calls are rejected (a WARN is logged at startup). Not needed for `ismd-tool-backend`. |
| `VALIDATION_GLOBAL_SPARQL_ENDPOINT` | production: `https://data.gov.cz/slovn%C3%ADky/sparql`; otherwise empty (global checks off) | NKD corpus SPARQL endpoint for global rules. Needs outbound access. If unreachable, the checks fail open. |
| `SERVER_SERVLET_CONTEXT_PATH` | `/validujeme` | Base path for all endpoints. |
| `MAX_UPLOAD_FILE_SIZE` | `10MB` | Applies to multipart uploads **and** `fileUrl` downloads. |
| `VALIDATION_RULES_TIMEOUT_MS` | `10000` | Maximum SHACL execution time for each request. |
| `VALIDATION_RULES_QUEUE_TIMEOUT_MS` | `30000` | Maximum wait for a free SHACL worker before the request is rejected as busy. |
| `VALIDATION_RULES_POOL_SIZE` | `0` (= max(2, CPUs)) | Number of SHACL worker threads. |
| `VALIDATION_RULES_ENABLE_ONTOLOGY_VIOLATION_DOWNLOAD` | `false` | **Keep `false` in production.** `true` returns output even when validation has ERRORs. |
| `JAVA_TOOL_OPTIONS` | set by Terraform | e.g. `-javaagent:/app/applicationinsights-agent.jar`. The heap is set separately (`MaxRAMPercentage=70`) in the ENTRYPOINT. |
| `APPLICATIONINSIGHTS_CONNECTION_STRING` | set by Terraform | App Insights destination, used only when the agent is enabled. |

Outbound network used at runtime:

- `slovník.gov.cz` (SSP import)
- the corpus endpoint (global validation)
- arbitrary HTTPS URLs (`fileUrl` import). Private, loopback and link-local addresses are blocked.

## Health

- Liveness: `/validujeme/actuator/health/liveness`
- Readiness: `/validujeme/actuator/health/readiness`

The app refuses to start if any rule enabled in `application.properties` fails to load, so a broken
rule set never reaches readiness.

## Release & deploy

1. Run the **Release Version** workflow (manual). It cuts `release-vX.Y.Z` from `dev`, runs the tests, and opens a "Release vX.Y.Z" PR to `main`.
2. Merging the PR tags `vX.Y.Z` and publishes `ghcr.io/datagov-cz/ismd-validator-backend:X.Y.Z`. That image is auto-deployed to TEST.
3. PROD: run **Trigger Deployment** manually with environment `prod` and the explicit version.
