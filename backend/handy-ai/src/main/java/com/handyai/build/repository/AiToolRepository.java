package com.handyai.build.repository;

import com.handyai.build.domain.AiTool;
import com.handyai.build.domain.PricingModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiToolRepository extends JpaRepository<AiTool, Long> {

    /**
     * Every read that leaves the service layer loads its category eagerly through an entity graph,
     * so no DTO mapping can trip over a detached lazy proxy.
     */
    @EntityGraph(attributePaths = "category")
    Optional<AiTool> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select t from AiTool t
            where (:query is null
                   or lower(t.name) like :query
                   or lower(t.tagline) like :query
                   or lower(t.description) like :query
                   or lower(t.tags) like :query)
              and (:categorySlug is null or t.category.slug = :categorySlug)
              and (:pricing is null or t.pricingModel = :pricing)
              and (:freePlanOnly = false
                   or t.pricingModel = com.handyai.build.domain.PricingModel.FREE
                   or t.pricingModel = com.handyai.build.domain.PricingModel.FREEMIUM)
              and (:minInr is null or t.priceMonthlyInr >= :minInr)
              and (:maxInr is null or t.priceMonthlyInr <= :maxInr)
              and (:minUsd is null or t.priceMonthlyUsd >= :minUsd)
              and (:maxUsd is null or t.priceMonthlyUsd <= :maxUsd)
            """)
    Page<AiTool> search(@Param("query") String query,
                        @Param("categorySlug") String categorySlug,
                        @Param("pricing") PricingModel pricing,
                        @Param("freePlanOnly") boolean freePlanOnly,
                        @Param("minInr") Long minInr,
                        @Param("maxInr") Long maxInr,
                        @Param("minUsd") Double minUsd,
                        @Param("maxUsd") Double maxUsd,
                        Pageable pageable);

    @EntityGraph(attributePaths = "category")
    List<AiTool> findByFeaturedTrueOrderByPopularityDesc(Pageable pageable);

    @EntityGraph(attributePaths = "category")
    List<AiTool> findAllByOrderByPopularityDesc(Pageable pageable);

    @EntityGraph(attributePaths = "category")
    List<AiTool> findByIdIn(List<Long> ids);

    long countByCategoryId(Long categoryId);
}
