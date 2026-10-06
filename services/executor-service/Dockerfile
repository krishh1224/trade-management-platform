# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies first for better build cache reuse
COPY services/executor-service/pom.xml /workspace/services/executor-service/pom.xml
RUN mvn -f /workspace/services/executor-service/pom.xml -DskipTests dependency:go-offline

# Copy source only after dependency resolution
COPY services/executor-service /workspace/services/executor-service

RUN mvn -f /workspace/services/executor-service/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /workspace/services/executor-service/target/*.jar /app/app.jar

USER appuser
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
