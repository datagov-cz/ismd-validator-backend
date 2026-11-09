### Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Install bash
RUN apk add --no-cache bash

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
