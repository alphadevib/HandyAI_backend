package com.handyai.build.ai;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Counts LLM tokens per UTC day, estimates what they cost, and enforces the daily token budget:
 * once {@link AiProperties#dailyTokenBudget()} is used up, chat falls back to its rule-based replies
 * until the next day. On your own machine the cost is zero, but the budget still caps how much CPU or
 * GPU time chat can take. In memory, so each instance keeps its own tally.
 */
@Component
public class AiUsageMeter {

    private static final Logger log = LoggerFactory.getLogger(AiUsageMeter.class);

    public record Usage(LocalDate date, long calls, long inputTokens, long outputTokens,
                        double costUsd) {

        static Usage empty(LocalDate date) {
            return new Usage(date, 0, 0, 0, 0);
        }

        public long totalTokens() {
            return inputTokens + outputTokens;
        }
    }

    /** Today's numbers next to the settings that bound them, for the admin dashboard. */
    public record Snapshot(boolean enabled, String model, Usage today, long dailyTokenBudget,
                           int maxTokensPerReply) {
    }

    private final AiProperties properties;
    private final AtomicReference<Usage> today;

    public AiUsageMeter(AiProperties properties) {
        this.properties = properties;
        this.today = new AtomicReference<>(Usage.empty(currentDate()));
    }

    public double costUsd(long inputTokens, long outputTokens) {
        return inputTokens * properties.inputUsdPerMillion() / 1_000_000d
                + outputTokens * properties.outputUsdPerMillion() / 1_000_000d;
    }

    /** Every answered call is recorded, including ones whose reply was not used. */
    public void record(long inputTokens, long outputTokens) {
        double cost = costUsd(inputTokens, outputTokens);
        Usage updated = today.updateAndGet(usage -> {
            Usage current = rolled(usage);
            return new Usage(current.date(), current.calls() + 1,
                    current.inputTokens() + inputTokens, current.outputTokens() + outputTokens,
                    current.costUsd() + cost);
        });
        log.info("LLM call ({}): {} input + {} output tokens; today {} of {} tokens, ~${}",
                properties.model(), inputTokens, outputTokens, updated.totalTokens(),
                properties.dailyTokenBudget(), String.format("%.4f", updated.costUsd()));
    }

    public boolean withinBudget() {
        return rolled(today.get()).totalTokens() < properties.dailyTokenBudget();
    }

    public Snapshot snapshot() {
        return new Snapshot(properties.enabled(), properties.model(), rolled(today.get()),
                properties.dailyTokenBudget(), properties.maxTokens());
    }

    private static Usage rolled(Usage usage) {
        LocalDate now = currentDate();
        return usage.date().equals(now) ? usage : Usage.empty(now);
    }

    private static LocalDate currentDate() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
