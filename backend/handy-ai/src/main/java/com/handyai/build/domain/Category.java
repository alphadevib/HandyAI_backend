package com.handyai.build.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(length = 300)
    private String description;

    /** Short emoji/glyph rendered by the UI so no icon assets are required. */
    @Column(length = 8)
    private String icon;

    /**
     * The tools in this category, most popular first: the inverse side of {@link AiTool#getCategory()}
     * ({@code mappedBy}), so it adds no column and is changed only through the tool. Lazy, and
     * loaded in batches when several categories need it, so a feed over a user's interests costs
     * one query rather than one per category.
     */
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
    @OrderBy("popularity desc")
    @BatchSize(size = 20)
    private List<AiTool> tools = new ArrayList<>();

    public Category() {
    }

    public Category(String name, String slug, String description, String icon) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.icon = icon;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<AiTool> getTools() {
        return tools;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }
}
