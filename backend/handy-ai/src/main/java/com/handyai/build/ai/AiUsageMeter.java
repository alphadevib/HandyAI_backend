package com.handyai.build.ai;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Counts Claude tokens and estimates their cost per UTC day, and enforces the daily budget: once
 * the estimate reaches {@link AiProperties#dailyBudgetUsd()}, chat falls back to its rule-based
 * replies until the next day. In memory, so each instance keeps its own tally.
 */
@Component
public class AiUsageMeter {

    private static final Logger log = LoggerFactory.getLogger(AiUsageMeter.class);

    public record Usage(LocalDate date, long calls, long inputTokens, long outputTokens,
                        double costUsd) {

        static Usage empty(LocalDate date) {
            return new Usage(date, 0, 0, 0, 0);
        }
    }

    /** Today's numbers next to the settings that bound them, for the admin dashboard. */
    public record Snapshot(boolean enabled, String model, Usage today, double dailyBudgetUsd,
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

    /** Every billed call is recorded, including ones whose answer was not used. */
    public void record(long inputTokens, long outputTokens) {
        double cost = costUsd(inputTokens, outputTokens);
        Usage updated = today.updateAndGet(usage -> {
            Usage current = rolled(usage);
            return new Usage(current.date(), current.calls() + 1,
                    current.inputTokens() + inputTokens, current.outputTokens() + outputTokens,
                    current.costUsd() + cost);
        });
        log.info("Claude call: {} input + {} output tokens, ~${} (today ~${} of ${})",
                inputTokens, outputTokens, String.format("%.4f", cost),
                String.format("%.4f", updated.costUsd()), properties.dailyBudgetUsd());
    }

    public boolean withinBudget() {
        return rolled(today.get()).costUsd() < properties.dailyBudgetUsd();
    }

    public Snapshot snapshot() {
        return new Snapshot(properties.enabled(), properties.model(), rolled(today.get()),
                properties.dailyBudgetUsd(), properties.maxTokens());
    }

    private static Usage rolled(Usage usage) {
        LocalDate now = currentDate();
        return usage.date().equals(now) ? usage : Usage.empty(now);
    }

    private static LocalDate currentDate() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
