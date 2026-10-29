package com.casoslegales.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@EnableConfigServer
@SpringBootApplication
public class LegalConfigApplication {
    public static void main(String[] args) {
        SpringApplication.run(LegalConfigApplication.class, args);
    }
}
