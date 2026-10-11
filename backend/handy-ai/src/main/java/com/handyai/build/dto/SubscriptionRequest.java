package com.handyai.build.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Adding a plan needs only {@code toolSlug}; the rest default to the catalogue's monthly price in
 * rupees starting today. Updating sends any subset of the other fields.
 */
public record SubscriptionRequest(
        String toolSlug,
        String billingCycle,
        String currency,
        @DecimalMin(value = "0.0", message = "The amount cannot be negative")
        Double amount,
        LocalDate startDate,
        String status,
        @Size(max = 300, message = "Keep notes under 300 characters")
        String notes) {
}
