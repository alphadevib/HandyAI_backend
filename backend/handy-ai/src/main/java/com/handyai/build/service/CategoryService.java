package com.handyai.build.service;

import com.handyai.build.domain.Category;
import com.handyai.build.dto.CategoryResponse;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.repository.AiToolRepository;
import com.handyai.build.repository.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AiToolRepository toolRepository;

    public CategoryService(CategoryRepository categoryRepository, AiToolRepository toolRepository) {
        this.categoryRepository = categoryRepository;
        this.toolRepository = toolRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> CategoryResponse.from(category,
                        toolRepository.countByCategoryId(category.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", slug));
        return CategoryResponse.from(category, toolRepository.countByCategoryId(category.getId()));
    }
}
