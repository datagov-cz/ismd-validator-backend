### Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Install bash
RUN apk add --no-cache bash

# Copy all files
COPY . .

# Make mvnw executable
RUN chmod +x mvnw

# Build arguments with defaults
ARG MODULE=.
ARG BUILD_ARGS="-DskipTests"
ARG MODULE_VERSION=unknown

# Build the specified module (or everything if not specified)
RUN if [ "$MODULE" = "." ]; then \
      ./mvnw clean install ${BUILD_ARGS}; \
    else \
      ./mvnw clean install -pl ${MODULE} -am ${BUILD_ARGS}; \
    fi

# Create a default target that builds everything
FROM builder AS all
# This stage is just an alias for builder when building everything

# Create a stage that does nothing, used as a default target
FROM scratch AS default
COPY --from=builder /app /app

### Runtime stage for common module
FROM eclipse-temurin:17-jre-alpine AS common-runtime
WORKDIR /app

# Get build arguments
ARG MODULE_VERSION=unknown

# Set environment variables
ENV APP_VERSION=${MODULE_VERSION}
ENV SPRING_PROFILES_ACTIVE=production

# Copy the built JAR
COPY --from=builder /app/ismd-backend-common/target/ismd-backend-common-*.jar app.jar

# Add labels for better image identification
LABEL org.opencontainers.image.title="ISMD Backend Common"
LABEL org.opencontainers.image.version="${MODULE_VERSION}"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

### Runtime stage for validator module
FROM eclipse-temurin:17-jre-alpine AS validator-runtime
WORKDIR /app

# Get build arguments
ARG MODULE_VERSION=unknown

# Set environment variables
ENV APP_VERSION=${MODULE_VERSION}
ENV SPRING_PROFILES_ACTIVE=production

# Copy the built JAR
COPY --from=builder /app/ismd-backend-validator/target/ismd-backend-validator-*.jar app.jar

# Add labels for better image identification
LABEL org.opencontainers.image.title="ISMD Backend Validator"
LABEL org.opencontainers.image.version="${MODULE_VERSION}"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]