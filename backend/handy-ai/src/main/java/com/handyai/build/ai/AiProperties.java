package com.handyai.build.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the LLM-written chat replies ({@code handyai.ai.*}), served by Ollama. When it is
 * switched off or unreachable, the chat keeps its built-in rule-based replies, so the app runs and
 * tests pass without a model.
 *
 * @param enabled              whether to ask Ollama at all
 * @param baseUrl              the Ollama server, e.g. http://localhost:11434
 * @param model                an Ollama model the server has pulled, e.g. qwen3:8b
 * @param think                let a thinking model (qwen3, deepseek-r1) reason first; slower
 * @param maxTokens            output cap per reply (Ollama's num_predict)
 * @param contextWindow        tokens the model reads per request (Ollama's num_ctx)
 * @param timeoutSeconds       read timeout; the first request also loads the model into memory
 * @param contextTokenBudget   most tokens of retrieved tool data put into one prompt
 * @param dailyTokenBudget     tokens per UTC day after which chat falls back to rules
 * @param inputUsdPerMillion   price of input tokens, for the cost estimate (0 on your own machine)
 * @param outputUsdPerMillion  price of output tokens, for the cost estimate
 */
@ConfigurationProperties(prefix = "handyai.ai")
public record AiProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("http://localhost:11434") String baseUrl,
        @DefaultValue("qwen3:8b") String model,
        @DefaultValue("false") boolean think,
        @DefaultValue("400") int maxTokens,
        @DefaultValue("4096") int contextWindow,
        @DefaultValue("120") int timeoutSeconds,
        @DefaultValue("1500") int contextTokenBudget,
        @DefaultValue("2000000") long dailyTokenBudget,
        @DefaultValue("0") double inputUsdPerMillion,
        @DefaultValue("0") double outputUsdPerMillion) {
}
