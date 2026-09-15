# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies first for better build cache reuse.
COPY trade-api/pom.xml /workspace/trade-api/pom.xml
RUN mvn -f /workspace/trade-api/pom.xml -DskipTests dependency:go-offline

# Copy source only after dependency resolution layers.
COPY trade-api /workspace/trade-api

RUN mvn -f /workspace/trade-api/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /workspace/trade-api/target/*.jar /app/app.jar

USER appuser
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
