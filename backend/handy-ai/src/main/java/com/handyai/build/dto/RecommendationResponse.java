package com.handyai.build.dto;

import java.util.List;

public record RecommendationResponse(String summary, List<Recommendation> recommendations) {

    public record Recommendation(ToolResponse tool, int matchScore, List<String> reasons) {
    }
}
