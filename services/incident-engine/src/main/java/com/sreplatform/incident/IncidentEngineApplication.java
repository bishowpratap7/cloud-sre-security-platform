package com.sreplatform.incident;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IncidentEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentEngineApplication.class, args);
    }
}
