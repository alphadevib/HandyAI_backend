package com.handyai.build.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    /**
     * The HTTP client for the Ollama API. A short connect timeout means a stopped Ollama costs the
     * chat a moment, not a hang; the read timeout allows for a model being loaded into memory.
     */
    @Bean
    public RestTemplate ollamaRestTemplate(AiProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        return new RestTemplate(factory);
    }
}
