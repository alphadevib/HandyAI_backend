package com.handyai.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.handyai.build.dto.CategoryResponse;
import com.handyai.build.dto.RegisterRequest;
import com.handyai.build.dto.ReviewRequest;
import com.handyai.build.dto.ReviewResponse;
import com.handyai.build.repository.OutboundClickRepository;
import com.handyai.build.service.AuthService;
import com.handyai.build.service.CategoryService;
import com.handyai.build.service.ReviewService;
import com.handyai.build.service.ToolService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Spring caching (@Cacheable / @CacheEvict) and background work (@Async). */
@SpringBootTest
@AutoConfigureMockMvc
class HandyAIPerformanceTests {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ToolService toolService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private AuthService authService;

    @Autowired
    private OutboundClickRepository clickRepository;

    @Autowired
    private MockMvc mvc;

    @Test
    void catalogueReadsComeFromTheCache() {
        List<CategoryResponse> first = categoryService.listAll();
        assertThat(categoryService.listAll()).isSameAs(first);
        assertThat(toolService.priceRanges("USD")).isSameAs(toolService.priceRanges("USD"));
    }

    @Test
    void writingAReviewEvictsThatToolsCachedReviews() {
        String slug = toolService.trending(1, null).get(0).slug();
        List<ReviewResponse> before = reviewService.listForTool(slug);
        assertThat(reviewService.listForTool(slug)).isSameAs(before);

        Long userId = authService.register(new RegisterRequest("Cache Reviewer",
                "cache-reviewer@handyai.test", "supersecret", "Designer")).user().id();
        reviewService.upsert(slug, userId, new ReviewRequest(5, "Saves me an hour a day"));

        assertThat(reviewService.listForTool(slug))
                .hasSize(before.size() + 1)
                .anySatisfy(review -> assertThat(review.comment()).isEqualTo("Saves me an hour a day"));
    }

    @Test
    void outboundClicksAreStoredInTheBackground() throws Exception {
        String slug = toolService.trending(2, null).get(1).slug();
        long before = clickRepository.count();

        mvc.perform(get("/api/go/" + slug).param("v", "async-visitor-0001"))
                .andExpect(status().isFound());

        long deadline = System.currentTimeMillis() + 5_000;
        while (clickRepository.count() == before && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertThat(clickRepository.count()).isEqualTo(before + 1);
    }
}
