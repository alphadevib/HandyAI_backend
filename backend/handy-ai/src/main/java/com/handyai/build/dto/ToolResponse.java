package com.handyai.build.dto;

import com.handyai.build.domain.AiTool;
import com.handyai.build.domain.PriceBook.Plans;
import java.util.List;

/** Flat view of a tool: everything the cards and the detail panel need, in one payload. */
public record ToolResponse(Long id, String name, String slug, String tagline, String description,
                           String websiteUrl, String pricingModel, String priceNote,
                           String categoryName, String categorySlug, String categoryIcon,
                           List<String> tags, int popularity, double rating, int ratingCount,
                           boolean featured, boolean favorite, String logoUrl,
                           boolean hasFreePlan, Plans plans) {

    public static ToolResponse from(AiTool tool, boolean favorite) {
        return new ToolResponse(
                tool.getId(),
                tool.getName(),
                tool.getSlug(),
                tool.getTagline(),
                tool.getDescription(),
                tool.getWebsiteUrl(),
                tool.getPricingModel().name(),
                tool.getPriceNote(),
                tool.getCategory().getName(),
                tool.getCategory().getSlug(),
                tool.getCategory().getIcon(),
                tool.tagList(),
                tool.getPopularity(),
                tool.averageRating(),
                tool.getRatingCount(),
                tool.isFeatured(),
                favorite,
                // Logos ship with the frontend as static files named after the slug.
                "/logos/" + tool.getSlug() + ".png",
                tool.hasFreePlan(),
                Plans.of(tool));
    }
}
