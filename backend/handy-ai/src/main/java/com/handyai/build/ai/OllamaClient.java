package com.handyai.build.ai;

import java.util.Optional;
import java.util.regex.Pattern;
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
 * Calls a local or self-hosted Ollama server's chat API ({@code POST /api/chat}) over plain HTTP
 * with {@link RestTemplate}. No API key: the model runs on hardware you control.
 *
 * <p>Every failure (switched off, Ollama not running, model not pulled, HTTP error, a reply cut off
 * at the token cap) comes back as an empty Optional, so the caller can always fall back to its
 * rule-based answer.
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    /** Some thinking models still inline their reasoning; it never belongs in a reply. */
    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");

    public record Reply(String text, String model, long inputTokens, long outputTokens) {
    }

    private final RestTemplate http;
    private final AiProperties properties;
    private final AiUsageMeter meter;
    private final JsonMapper json = JsonMapper.builder().build();

    public OllamaClient(RestTemplate ollamaRestTemplate, AiProperties properties,
                        AiUsageMeter meter) {
        this.http = ollamaRestTemplate;
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
        try {
            String raw = http.postForObject(properties.baseUrl() + "/api/chat",
                    new HttpEntity<>(json.writeValueAsString(requestBody(system, userMessage)), headers),
                    String.class);
            return parse(json.readTree(raw));
        } catch (RestClientException | JacksonException ex) {
            log.warn("Ollama request failed, using the rule-based reply: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private ObjectNode requestBody(String system, String userMessage) {
        ObjectNode body = json.createObjectNode();
        body.put("model", properties.model());
        body.put("stream", false);
        body.put("think", properties.think());
        body.putObject("options")
                .put("num_predict", properties.maxTokens())
                .put("num_ctx", properties.contextWindow());
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", userMessage);
        return body;
    }

    private Optional<Reply> parse(JsonNode response) {
        long input = response.path("prompt_eval_count").asLong(0);
        long output = response.path("eval_count").asLong(0);
        meter.record(input, output);

        // "length" means the reply hit num_predict and would end mid-sentence.
        String doneReason = response.path("done_reason").asString("");
        if (!response.path("done").asBoolean(false) || !"stop".equals(doneReason)) {
            log.warn("Ollama stopped with '{}', using the rule-based reply", doneReason);
            return Optional.empty();
        }
        String text = THINK_BLOCK.matcher(response.path("message").path("content").asString(""))
                .replaceAll("").trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Reply(text, response.path("model").asString(properties.model()),
                input, output));
    }
}
