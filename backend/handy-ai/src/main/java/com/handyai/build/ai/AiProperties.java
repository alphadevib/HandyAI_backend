package com.handyai.build.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the Claude-written chat replies ({@code handyai.ai.*}). With no API key the chat
 * keeps its built-in rule-based replies, so the app runs and tests pass without one.
 *
 * @param apiKey               Anthropic API key; blank turns the LLM off
 * @param baseUrl              API root, overridable for a proxy
 * @param model                Claude model id
 * @param maxTokens            output cap per reply (includes thinking); replies are short by design
 * @param effort               output_config.effort; chat does well at low
 * @param timeoutSeconds       read timeout for one request
 * @param contextTokenBudget   most tokens of retrieved tool data put into one prompt
 * @param dailyBudgetUsd       estimated spend per UTC day after which chat falls back to rules
 * @param inputUsdPerMillion   price of input tokens, for the cost estimate
 * @param outputUsdPerMillion  price of output tokens, for the cost estimate
 */
@ConfigurationProperties(prefix = "handyai.ai")
public record AiProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://api.anthropic.com") String baseUrl,
        @DefaultValue("claude-opus-5-5") String model,
        @DefaultValue("4096") int maxTokens,
        @DefaultValue("low") String effort,
        @DefaultValue("60") int timeoutSeconds,
        @DefaultValue("1500") int contextTokenBudget,
        @DefaultValue("2.00") double dailyBudgetUsd,
        @DefaultValue("4.00") double inputUsdPerMillion,
        @DefaultValue("20.00") double outputUsdPerMillion) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
