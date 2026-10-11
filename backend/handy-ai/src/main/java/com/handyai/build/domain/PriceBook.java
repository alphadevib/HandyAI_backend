package com.handyai.build.domain;

import java.util.Locale;

/**
 * Currency rules shared by the catalogue, the marketplace filters and subscription tracking.
 *
 * <p>Vendor list prices are curated in US dollars. Rupee prices are a fixed conversion, not the
 * vendor's regional price, and the UI labels every figure as indicative for that reason.
 */
public final class PriceBook {

    /** Fixed conversion rate used everywhere a rupee figure is derived from a dollar price. */
    public static final double USD_TO_INR = 88.0;

    private PriceBook() {
    }

    public enum Currency {
        INR, USD;

        public static Currency parse(String value) {
            if (value == null || value.isBlank()) {
                return INR;
            }
            return Currency.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    public enum BillingCycle {
        MONTHLY(1), QUARTERLY(3), ANNUAL(12);

        private final int months;

        BillingCycle(int months) {
            this.months = months;
        }

        public int months() {
            return months;
        }
    }

    public static long toInr(double usd) {
        return Math.round(usd * USD_TO_INR);
    }

    public static double roundUsd(double usd) {
        return Math.round(usd * 100d) / 100d;
    }

    /** One amount in both currencies, so the UI can switch currency without another request. */
    public record Money(double usd, long inr) {

        public static Money ofUsd(double usd) {
            return new Money(roundUsd(usd), toInr(usd));
        }
    }

    /**
     * The three ways a plan can be billed. Vendors rarely sell a quarterly plan, so quarterly is
     * three months at the monthly price, which is what someone paying monthly spends per quarter.
     */
    public record Plans(Money monthly, Money quarterly, Money annual, boolean paidPlanAvailable) {

        public static Plans of(AiTool tool) {
            Double monthly = tool.getPriceMonthlyUsd();
            if (monthly == null) {
                return null;
            }
            double annual = tool.getPriceAnnualUsd() == null ? monthly * 12 : tool.getPriceAnnualUsd();
            // Quarterly is derived from the rounded monthly figures, so it is exactly three times
            // what the card shows as the monthly price in either currency.
            Money perMonth = Money.ofUsd(monthly);
            Money perQuarter = new Money(roundUsd(perMonth.usd() * 3), perMonth.inr() * 3);
            return new Plans(perMonth, perQuarter, Money.ofUsd(annual), monthly > 0);
        }

        public Money forCycle(BillingCycle cycle) {
            return switch (cycle) {
                case MONTHLY -> monthly;
                case QUARTERLY -> quarterly;
                case ANNUAL -> annual;
            };
        }
    }
}
