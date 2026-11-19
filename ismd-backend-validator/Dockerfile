### Build stage
# Ubuntu-based Temurin tags (not -alpine) are published multi-arch
# (linux/amd64 + linux/arm64). The -alpine variants are amd64-only, which
# breaks `docker build`/`pull` on Apple Silicon. bash ships in these images,
# so no extra install is needed.
# Both base images are pinned to the multi-arch index digest (same as ismd-tool-backend);
# Dependabot (docker ecosystem) raises the bump PRs.
FROM eclipse-temurin:24-jdk@sha256:7493205ffe6caa8074fa8a06a276bb1c5ac41d3dd0fd43a0db66d7f776e80b3e AS builder
WORKDIR /app

# Copy the validator module from repo root context (self-contained: all deps come from Maven Central)
COPY ismd-backend-validator/ ./ismd-backend-validator

# Switch to validator module root (contains mvnw)
WORKDIR /app/ismd-backend-validator

# Normalize line endings (handle CRLF from Windows checkouts) and make mvnw executable
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Build arguments with defaults
ARG MODULE_VERSION=unknown
ARG REVISION=0.0.1

# Build the validator application
# Pass -Drevision to set version from Git tag
RUN ./mvnw clean install -Drevision=${REVISION} -DskipTests

### Runtime stage
FROM eclipse-temurin:24-jre@sha256:8cb2387a28af84cf0db0948d9c67d4480192f4e567027a3963f145d218e8b4f2 AS runtime
WORKDIR /app

# Get build arguments
ARG MODULE_VERSION=unknown

# Set environment variables
ENV APP_VERSION=${MODULE_VERSION}
ENV SPRING_PROFILES_ACTIVE=production

# Copy the built JAR
COPY --from=builder /app/ismd-backend-validator/target/ismd-backend-validator-*.jar app.jar

# Application Insights Java agent — baked in but NOT activated here on purpose.
# Activation is a runtime toggle: Terraform sets
# JAVA_TOOL_OPTIONS=-javaagent:/app/applicationinsights-agent.jar per env
# (enable_app_insights_agent), so one image serves all envs and telemetry can be
# flipped on/off without a rebuild. The agent reads APPLICATIONINSIGHTS_CONNECTION_STRING
# (injected by TF) for its destination. The checksum must change with the version.
ADD --checksum=sha256:e81ef99fd30444f6f1da70cd31db5e47f8e6906acbbc9199cac3b390dc6cfedf \
    https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.6.2/applicationinsights-agent-3.6.2.jar /app/applicationinsights-agent.jar

# Add labels for better image identification
LABEL org.opencontainers.image.title="ISMD Backend Validator"
LABEL org.opencontainers.image.version="${MODULE_VERSION}"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
