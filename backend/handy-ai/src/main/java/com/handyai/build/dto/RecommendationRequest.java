package com.handyai.build.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

/** What the visitor tells us on the home page or in the chat; every field is optional. */
public record RecommendationRequest(
        @Size(max = 400, message = "Keep the description under 400 characters")
        String goal,

        @Size(max = 120)
        String profession,

        List<String> categorySlugs,

        String pricing,

        @Min(value = 1, message = "Ask for between 1 and 24 recommendations")
        @Max(value = 24, message = "Ask for between 1 and 24 recommendations")
        Integer limit,

        /** Only tools that can be used without paying (FREE or FREEMIUM). */
        Boolean freePlanOnly,

        /** Upper bound on the entry plan's monthly price, in {@code currency}. */
        @DecimalMin(value = "0.0", message = "The budget cannot be negative")
        Double maxMonthlyPrice,

        String currency,

        /** Tools the visitor has already been shown, for "show me something else". */
        List<String> excludeSlugs) {

    /** The original five-field form, used by the home page matcher and the tests. */
    public RecommendationRequest(String goal, String profession, List<String> categorySlugs,
                                 String pricing, Integer limit) {
        this(goal, profession, categorySlugs, pricing, limit, null, null, null, null);
    }
}
