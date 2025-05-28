package com.dia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ServicesConfiguration {

    @Bean
    public Clock systemDefaultZoneClock() {
        return Clock.system(ZoneId.systemDefault());
    }
}
