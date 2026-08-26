package com.smartjobtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.smartjobtracker.config.JobProviderConfig;
import com.smartjobtracker.config.AiMatchingConfig;
import com.smartjobtracker.config.GmailConfig;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JobProviderConfig.class, AiMatchingConfig.class, GmailConfig.class})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
