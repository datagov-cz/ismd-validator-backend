### Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Install bash
RUN apk add --no-cache bash

# Copy validator and common modules from repo root context
COPY ismd-backend-validator/ ./ismd-backend-validator
COPY ismd-validator-common/ ./ismd-validator-common

# Switch to validator module root (contains mvnw)
WORKDIR /app/ismd-backend-validator

# Normalize line endings (handle CRLF from Windows checkouts) and make mvnw executable
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Build arguments with defaults
ARG MODULE_VERSION=unknown
ARG REVISION=0.0.1
ARG GITHUB_TOKEN
ARG GITHUB_ACTOR
ARG GITHUB_REPOSITORY
ARG USE_LOCAL_COMMON=true

# Configure Maven to authenticate with GitHub Packages
# Always needed because Maven verifies dependencies against remote repos
RUN if [ -n "${GITHUB_TOKEN}" ] && [ -n "${GITHUB_ACTOR}" ]; then \
      echo "Configuring Maven for GitHub Packages..."; \
      mkdir -p ~/.m2 && \
      echo '<settings><servers><server>' > ~/.m2/settings.xml && \
      echo '<id>github</id>' >> ~/.m2/settings.xml && \
      echo "<username>${GITHUB_ACTOR}</username>" >> ~/.m2/settings.xml && \
      echo "<password>${GITHUB_TOKEN}</password>" >> ~/.m2/settings.xml && \
      echo '</server></servers></settings>' >> ~/.m2/settings.xml; \
    else \
      echo "No GitHub credentials provided, skipping Maven auth setup"; \
    fi

# Set GITHUB_REPOSITORY as environment variable for Maven
ENV GITHUB_REPOSITORY=${GITHUB_REPOSITORY}

# Pre-install common library locally if present and USE_LOCAL_COMMON=true (dev builds)
# Production builds will download from GitHub Packages instead
RUN if [ "$USE_LOCAL_COMMON" = "true" ] && [ -d ../ismd-validator-common ]; then \
      echo "Installing common library from local repo (dev build)..."; \
      ./mvnw -f ../ismd-validator-common/pom.xml -DskipTests install; \
    else \
      echo "Common library will be downloaded from GitHub Packages (prod build)"; \
    fi

# Build the validator application
# Pass -Drevision to set version from Git tag
RUN ./mvnw clean install -Drevision=${REVISION} -DskipTests

### Runtime stage
FROM eclipse-temurin:17-jre-alpine AS runtime
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
# (injected by TF) for its destination.
ADD https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.6.2/applicationinsights-agent-3.6.2.jar /app/applicationinsights-agent.jar

# Add labels for better image identification
LABEL org.opencontainers.image.title="ISMD Backend Validator"
LABEL org.opencontainers.image.version="${MODULE_VERSION}"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
