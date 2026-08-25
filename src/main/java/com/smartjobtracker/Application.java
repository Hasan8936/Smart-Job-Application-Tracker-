package com.smartjobtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.smartjobtracker.config.JobProviderConfig;
import com.smartjobtracker.config.AiMatchingConfig;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JobProviderConfig.class, AiMatchingConfig.class})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
