package com.handyai.build.dto;

import com.handyai.build.domain.Category;

public record CategoryResponse(Long id, String name, String slug, String description, String icon,
                               long toolCount) {

    public static CategoryResponse from(Category category, long toolCount) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.getIcon(), toolCount);
    }
}
