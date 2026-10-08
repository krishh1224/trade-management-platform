# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies first for better build cache reuse.
COPY services/order-service/pom.xml /workspace/services/order-service/pom.xml
RUN mvn -f /workspace/services/order-service/pom.xml -DskipTests dependency:go-offline

# Copy source only after dependency resolution layers.
COPY services/order-service /workspace/services/order-service

RUN mvn -f /workspace/services/order-service/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=build /workspace/services/order-service/target/*.jar /app/app.jar

USER appuser
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
