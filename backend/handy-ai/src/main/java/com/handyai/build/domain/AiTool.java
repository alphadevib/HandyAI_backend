package com.handyai.build.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.hibernate.annotations.Formula;

/**
 * A single AI platform/tool that can be recommended to a user.
 *
 * <p>Relations are deliberately uni-directional and lazy: no entity owns a collection of another
 * entity, which keeps every read a single predictable query and makes recursive JSON impossible.
 */
@Entity
@Table(name = "ai_tools", indexes = {
        @Index(name = "idx_ai_tools_slug", columnList = "slug", unique = true),
        @Index(name = "idx_ai_tools_category", columnList = "category_id")
})
public class AiTool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 140)
    private String slug;

    @Column(nullable = false, length = 200)
    private String tagline;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "website_url", nullable = false, length = 400)
    private String websiteUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_model", nullable = false, length = 20)
    private PricingModel pricingModel = PricingModel.FREEMIUM;

    @Column(name = "price_note", length = 120)
    private String priceNote;

    /**
     * Entry-level paid plan, billed monthly and billed yearly. Zero means the tool has no paid plan
     * at all; {@code null} means the price is not known yet. The rupee columns are stored rather
     * than converted per request so the marketplace's price filters compare exact numbers.
     */
    @Column(name = "price_monthly_usd")
    private Double priceMonthlyUsd;

    @Column(name = "price_annual_usd")
    private Double priceAnnualUsd;

    @Column(name = "price_monthly_inr")
    private Long priceMonthlyInr;

    @Column(name = "price_annual_inr")
    private Long priceAnnualInr;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Comma separated keywords, kept as a plain column so a tool list never needs a second query. */
    @Column(name = "tags", length = 400)
    private String tags;

    /** Hand-curated signal (0-100) used as the tie breaker in recommendations. */
    @Column(name = "popularity", nullable = false)
    private int popularity = 50;

    @Column(name = "rating_sum", nullable = false)
    private int ratingSum;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    /**
     * The average rating, computed by the database so "best rated" can sort on it. Sorting on the
     * stored sum instead would rank ten 3-star reviews above five 5-star ones.
     */
    @Formula("case when rating_count = 0 then 0 else rating_sum * 1.0 / rating_count end")
    private double ratingAverage;

    @Column(name = "featured", nullable = false)
    private boolean featured;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public List<String> tagList() {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }

    /** A FREE or FREEMIUM tool can be used without paying anything. */
    public boolean hasFreePlan() {
        return pricingModel == PricingModel.FREE || pricingModel == PricingModel.FREEMIUM;
    }

    public double averageRating() {
        if (ratingCount == 0) {
            return 0d;
        }
        return Math.round((ratingSum * 10d) / ratingCount) / 10d;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getTagline() {
        return tagline;
    }

    public void setTagline(String tagline) {
        this.tagline = tagline;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public PricingModel getPricingModel() {
        return pricingModel;
    }

    public void setPricingModel(PricingModel pricingModel) {
        this.pricingModel = pricingModel;
    }

    public String getPriceNote() {
        return priceNote;
    }

    public void setPriceNote(String priceNote) {
        this.priceNote = priceNote;
    }

    public Double getPriceMonthlyUsd() {
        return priceMonthlyUsd;
    }

    public void setPriceMonthlyUsd(Double priceMonthlyUsd) {
        this.priceMonthlyUsd = priceMonthlyUsd;
    }

    public Double getPriceAnnualUsd() {
        return priceAnnualUsd;
    }

    public void setPriceAnnualUsd(Double priceAnnualUsd) {
        this.priceAnnualUsd = priceAnnualUsd;
    }

    public Long getPriceMonthlyInr() {
        return priceMonthlyInr;
    }

    public void setPriceMonthlyInr(Long priceMonthlyInr) {
        this.priceMonthlyInr = priceMonthlyInr;
    }

    public Long getPriceAnnualInr() {
        return priceAnnualInr;
    }

    public void setPriceAnnualInr(Long priceAnnualInr) {
        this.priceAnnualInr = priceAnnualInr;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public int getPopularity() {
        return popularity;
    }

    public void setPopularity(int popularity) {
        this.popularity = popularity;
    }

    public int getRatingSum() {
        return ratingSum;
    }

    public void setRatingSum(int ratingSum) {
        this.ratingSum = ratingSum;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(int ratingCount) {
        this.ratingCount = ratingCount;
    }

    public double getRatingAverage() {
        return ratingAverage;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
