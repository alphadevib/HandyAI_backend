package com.handyai.build.ai;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Calls the Anthropic Messages API ({@code POST /v1/messages}) over plain HTTP with
 * {@link RestTemplate}.
 *
 * <p>Every failure (no key, network, HTTP error, a refusal, a reply cut off at {@code max_tokens})
 * comes back as an empty Optional, so the caller can always fall back to its rule-based answer.
 */
@Component
public class ClaudeClient {

    static final String API_VERSION = "2023-06-01";
    /** Lets the API retry a safety-classifier decline on another model instead of refusing. */
    static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

    public record Reply(String text, String model, long inputTokens, long outputTokens) {
    }

    private final RestTemplate http;
    private final AiProperties properties;
    private final AiUsageMeter meter;
    private final JsonMapper json = JsonMapper.builder().build();

    public ClaudeClient(RestTemplate anthropicRestTemplate, AiProperties properties,
                        AiUsageMeter meter) {
        this.http = anthropicRestTemplate;
        this.properties = properties;
        this.meter = meter;
    }

    public boolean available() {
        return properties.enabled() && meter.withinBudget();
    }

    public Optional<Reply> complete(String system, String userMessage) {
        if (!available()) {
            return Optional.empty();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", properties.apiKey());
        headers.set("anthropic-version", API_VERSION);
        headers.set("anthropic-beta", FALLBACK_BETA);
        try {
            String raw = http.postForObject(properties.baseUrl() + "/v1/messages",
                    new HttpEntity<>(json.writeValueAsString(requestBody(system, userMessage)), headers),
                    String.class);
            return parse(json.readTree(raw));
        } catch (RestClientException | JacksonException ex) {
            log.warn("Claude request failed, using the rule-based reply: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private ObjectNode requestBody(String system, String userMessage) {
        ObjectNode body = json.createObjectNode();
        body.put("model", properties.model());
        body.put("max_tokens", properties.maxTokens());
        body.putObject("output_config").put("effort", properties.effort());
        body.put("fallbacks", "default");
        body.put("system", system);
        body.putArray("messages").addObject()
                .put("role", "user")
                .put("content", userMessage);
        return body;
    }

    private Optional<Reply> parse(JsonNode response) {
        JsonNode usage = response.path("usage");
        long input = usage.path("input_tokens").asLong(0)
                + usage.path("cache_creation_input_tokens").asLong(0)
                + usage.path("cache_read_input_tokens").asLong(0);
        long output = usage.path("output_tokens").asLong(0);
        meter.record(input, output);

        // Check why generation stopped before reading content: a refusal can arrive with no text,
        // and a reply cut off at max_tokens would end mid-sentence.
        String stopReason = response.path("stop_reason").asString("");
        if (!"end_turn".equals(stopReason)) {
            log.warn("Claude stopped with '{}', using the rule-based reply", stopReason);
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asString(""))) {
                text.append(block.path("text").asString(""));
            }
        }
        if (text.toString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Reply(text.toString().trim(),
                response.path("model").asString(properties.model()), input, output));
    }
}
