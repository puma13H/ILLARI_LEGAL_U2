package com.illarilegal.ordenlegal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.cloud.openfeign.EnableFeignClients
@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class OrdenLegalMsApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrdenLegalMsApplication.class, args);
    }
}
