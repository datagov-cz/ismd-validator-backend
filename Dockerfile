### Build stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

ARG SPRING_PROFILES_ACTIVE=production

# Install bash
RUN apk add --no-cache bash

# Copy maven files
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Make mvnw executable
RUN chmod +x mvnw

# Copy the source code
COPY src/ ./src/

# Build the application
RUN ./mvnw package -DskipTests -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}


### Runtime stage
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app
ARG SPRING_PROFILES_ACTIVE=production
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]