package com.handyai.build.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/** An idea, bug report or tool request from a visitor. Only the admin can read these. */
@Entity
@Table(name = "suggestions", indexes = @Index(name = "idx_suggestion_status", columnList = "status"))
public class Suggestion {

    public enum Category {
        IDEA, TOOL_REQUEST, BUG, OTHER
    }

    public enum Status {
        NEW, PLANNED, DONE, DISMISSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The member who sent it; null when a guest did. */
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "contact_name", length = 80)
    private String name;

    /** Optional, so the admin can reply to a guest. */
    @Column(name = "contact_email", length = 160)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category = Category.IDEA;

    @Column(nullable = false, length = 1000)
    private String message;

    /** The page the visitor was on, which often explains the suggestion. */
    @Column(length = 120)
    private String page;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.NEW;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPage() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
