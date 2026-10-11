package com.handyai.build.dto;

import com.handyai.build.dto.RecommendationResponse.Recommendation;
import java.util.List;

/** The assistant's answer, the tools it suggests, and the context to send with the next turn. */
public record ChatResponse(String reply, List<Recommendation> recommendations,
                           ChatRequest.Context context, List<String> suggestions) {
}
