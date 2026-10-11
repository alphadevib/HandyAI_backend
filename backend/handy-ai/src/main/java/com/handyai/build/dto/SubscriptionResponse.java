package com.handyai.build.dto;

import com.handyai.build.domain.PriceBook.Money;
import java.time.LocalDate;
import java.util.List;

public record SubscriptionResponse(Long id, ToolResponse tool, String billingCycle, String currency,
                                   double amount, LocalDate startDate, LocalDate nextRenewal,
                                   String status, String notes, Money monthlyEquivalent) {

    /** The whole list plus what it adds up to, in both currencies. */
    public record Overview(List<SubscriptionResponse> subscriptions, int activeCount,
                           Money monthlySpend, Money annualSpend,
                           SubscriptionResponse nextRenewal) {
    }
}
