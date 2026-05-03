package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Bean de {@link RestClient} dedicado al microservicio IA.
 * Timeouts cortos para evitar bloquear las funciones core si el modelo cae.
 */
@Configuration
public class IaServiceConfig {

    @Value("${ia.service.url:http://localhost:8001}")
    private String baseUrl;

    @Value("${ia.service.timeout-ms:8000}")
    private long timeoutMs;

    @Bean
    public RestClient iaServiceRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Math.min(timeoutMs, 4000));
        factory.setReadTimeout((int) timeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public Duration timeout() {
        return Duration.ofMillis(timeoutMs);
    }
}
