package com.ab.fundperf.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class OllamaTimeoutConfig {

    @Bean
    public RestClient.Builder ollamaRestClientBuilder() {
        var requestFactory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newHttpClient());
        requestFactory.setReadTimeout(Duration.ofMinutes(15));
        return RestClient.builder().requestFactory(requestFactory);
    }
}