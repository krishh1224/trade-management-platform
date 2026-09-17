package com.neueda.learning;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot application for Trade Executor service.
 * Consumes ORDER_PLACED events from Kafka, executes trades, and publishes execution results.
 */
@SpringBootApplication
@MapperScan("com.neueda.learning.mapper")
public class ExecutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutorApplication.class, args);
    }

}
