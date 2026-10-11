package com.handyai.build.controller;

import com.handyai.build.dto.CategoryResponse;
import com.handyai.build.service.CategoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.listAll();
    }

    @GetMapping("/{slug}")
    public CategoryResponse detail(@PathVariable String slug) {
        return categoryService.getBySlug(slug);
    }
}
