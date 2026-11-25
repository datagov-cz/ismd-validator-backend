### Build stage
# Ubuntu-based Temurin tags (not -alpine) are published multi-arch
# (linux/amd64 + linux/arm64). The -alpine variants are amd64-only, which
# breaks `docker build`/`pull` on Apple Silicon. bash ships in these images,
# so no extra install is needed.
# Both base images are pinned to the multi-arch index digest (same as ismd-tool-backend);
# Dependabot (docker ecosystem) raises the bump PRs.
FROM eclipse-temurin:17-jdk@sha256:7a0b2bc7e1372bb05ee361fb79e8a9e822df47343b8716253b3bbaa75060653e AS builder
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
FROM eclipse-temurin:17-jre@sha256:541b08fc7f494edf14e436ff6bfb55767e4a115c87bfc547b8c5de957b58737f AS runtime
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
# --chmod: ADD from a URL creates the file 0600 root-owned, which the non-root runtime user could not read.
ADD --checksum=sha256:e81ef99fd30444f6f1da70cd31db5e47f8e6906acbbc9199cac3b390dc6cfedf --chmod=0644 \
    https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.6.2/applicationinsights-agent-3.6.2.jar /app/applicationinsights-agent.jar

# Add labels for better image identification
LABEL org.opencontainers.image.title="ISMD Backend Validator"
LABEL org.opencontainers.image.version="${MODULE_VERSION}"

# Run as an unprivileged user. Only /tmp (Tomcat work dir, multipart buffering) needs to be writable.
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
USER 10001:10001

EXPOSE 8080
# Heap sized from the container memory limit (JVM default is only 25%). 70 matches ismd-tool-backend and leaves
# room for metaspace, thread stacks and the App Insights agent. Kept on the command line rather than in
# JAVA_TOOL_OPTIONS, which Terraform owns (App Insights agent); command-line flags take precedence anyway.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
