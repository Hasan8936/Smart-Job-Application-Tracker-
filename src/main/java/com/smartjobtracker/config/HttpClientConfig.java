package com.smartjobtracker.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestClientCustomizer timeoutCustomizer() {
        return builder -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(5));
            factory.setReadTimeout(Duration.ofSeconds(30));
            builder.requestFactory(factory);
        };
    }

    /** Dedicated builder for JobSpy — scraping 4 job sites can take up to 2 minutes. */
    @Bean("jobSpyRestClientBuilder")
    public RestClient.Builder jobSpyRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(120));
        return RestClient.builder().requestFactory(factory);
    }
}
