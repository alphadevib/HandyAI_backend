package com.handyai.build.service;

import com.handyai.build.domain.Category;
import com.handyai.build.dto.CategoryResponse;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.repository.AiToolRepository;
import com.handyai.build.repository.CategoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Two queries in all (categories, then every count at once) instead of one count per category,
     * and the result is reused for {@link #CACHE_FOR}: the catalogue only changes when it is seeded.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        Cached cached = cache;
        if (cached != null && cached.loadedAt().plus(CACHE_FOR).isAfter(Instant.now())) {
            return cached.categories();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : toolRepository.countPerCategory()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        List<CategoryResponse> categories = categoryRepository.findAllByOrderByNameAsc().stream()
                .map(category -> CategoryResponse.from(category, counts.getOrDefault(category.getId(), 0L)))
                .toList();
        cache = new Cached(categories, Instant.now());
        return categories;
    }

    private record Cached(List<CategoryResponse> categories, Instant loadedAt) {
    }

    static final Duration CACHE_FOR = Duration.ofMinutes(5);
    private volatile Cached cache;

    @Transactional(readOnly = true)
    public CategoryResponse getBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", slug));
        return CategoryResponse.from(category, toolRepository.countByCategoryId(category.getId()));
    }
}
