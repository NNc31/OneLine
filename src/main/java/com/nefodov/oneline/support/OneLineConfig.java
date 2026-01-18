package com.nefodov.oneline.support;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(OneLineProperties.class)
public class OneLineConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
