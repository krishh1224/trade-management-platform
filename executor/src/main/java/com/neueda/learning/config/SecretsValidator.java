package com.neueda.learning.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Validates critical configuration at application startup.
 * Fails fast if required environment variables are missing or invalid.
 */
@Component
public class SecretsValidator implements ApplicationRunner {

  @Value("${auth.jwt.shared-secret:}")
  private String jwtSecret;

  @Value("${spring.datasource.url:}")
  private String datasourceUrl;

  @Value("${spring.datasource.username:}")
  private String datasourceUsername;

  @Value("${spring.datasource.password:}")
  private String datasourcePassword;

  @Value("${spring.kafka.bootstrap-servers:}")
  private String kafkaBootstrapServers;

  @Override
  public void run(ApplicationArguments args) throws Exception {
    validateJwtSecret();
    validateDatabaseConfig();
    validateKafkaConfig();
  }

  /**
   * Validates JWT_SECRET is set and meets minimum length requirement.
   * JWT tokens signed with HS256 require at least 32 bytes (256 bits) for security.
   *
   * @throws IllegalStateException if JWT_SECRET is invalid
   */
  private void validateJwtSecret() {
    if (jwtSecret == null || jwtSecret.isBlank()) {
      throw new IllegalStateException(
          "CRITICAL: JWT_SECRET environment variable is not set. "
              + "This is required for token verification. "
              + "Generate with: openssl rand -base64 32");
    }

    // JWT_SECRET must be at least 32 bytes (256 bits) for HS256
    // base64 encoding: 4 characters encode 3 bytes, so 32 bytes = 44 base64 chars
    if (jwtSecret.getBytes().length < 32) {
      throw new IllegalStateException(
          "CRITICAL: JWT_SECRET is too short. Minimum 32 bytes required for HS256 security. "
              + "Current: "
              + jwtSecret.getBytes().length
              + " bytes. "
              + "Generate with: openssl rand -base64 32");
    }

    System.out.println("✓ JWT_SECRET validation passed (length: " + jwtSecret.getBytes().length
        + " bytes)");
  }

  /**
   * Validates database configuration is present.
   *
   * @throws IllegalStateException if database config is invalid
   */
  private void validateDatabaseConfig() {
    if (datasourceUrl == null || datasourceUrl.isBlank()) {
      throw new IllegalStateException(
          "CRITICAL: SPRING_DATASOURCE_URL is not set. "
              + "Set POSTGRES_HOST and POSTGRES_DB in environment variables.");
    }

    if (datasourceUsername == null || datasourceUsername.isBlank()) {
      throw new IllegalStateException(
          "CRITICAL: SPRING_DATASOURCE_USERNAME is not set. "
              + "Set POSTGRES_USER in environment variables.");
    }

    if (datasourcePassword == null || datasourcePassword.isBlank()) {
      throw new IllegalStateException(
          "CRITICAL: SPRING_DATASOURCE_PASSWORD is not set. "
              + "Set POSTGRES_PASSWORD in environment variables.");
    }

    System.out.println("✓ Database configuration validation passed (" + datasourceUrl + ")");
  }

  /**
   * Validates Kafka bootstrap servers are configured.
   *
   * @throws IllegalStateException if Kafka config is invalid
   */
  private void validateKafkaConfig() {
    if (kafkaBootstrapServers == null || kafkaBootstrapServers.isBlank()) {
      throw new IllegalStateException(
          "CRITICAL: KAFKA_BOOTSTRAP_SERVERS environment variable is not set. "
              + "Set to 'kafka:29092' for Docker or 'localhost:8085' for local development.");
    }

    System.out.println("✓ Kafka configuration validation passed (" + kafkaBootstrapServers + ")");
  }
}
