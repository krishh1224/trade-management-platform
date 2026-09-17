# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies first for better build cache reuse
COPY executor/pom.xml /workspace/executor/pom.xml
RUN mvn -f /workspace/executor/pom.xml -DskipTests dependency:go-offline

# Copy source only after dependency resolution
COPY executor /workspace/executor

RUN mvn -f /workspace/executor/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /workspace/executor/target/*.jar /app/app.jar

USER appuser
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
