package com.handyai.build.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * One chat turn. The server keeps no conversation state: the client sends back the
 * {@link Context} it received last time, so any server instance can answer the next message.
 */
public record ChatRequest(
        @NotBlank(message = "Type or say what you need")
        @Size(max = 500, message = "Keep each message under 500 characters")
        String message,

        @Valid
        Context context) {

    public record Context(
            @Size(max = 400) String goal,
            @Size(max = 120) String profession,
            Boolean freePlanOnly,
            Double maxMonthlyPrice,
            String currency,
            @Size(max = 60) List<String> shownSlugs) {

        public static Context empty() {
            return new Context(null, null, false, null, null, List.of());
        }
    }
}
