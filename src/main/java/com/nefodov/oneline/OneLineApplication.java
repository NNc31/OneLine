package com.nefodov.oneline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OneLineApplication {

    public static void main(String[] args) {
        SpringApplication.run(OneLineApplication.class, args);
    }

}
