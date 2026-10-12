package com.handyai.build.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * How much Claude one user has used: replies, tokens and estimated cost.
 *
 * <p>A one-to-one with {@link User} that shares the user's primary key ({@code @MapsId}): the row's
 * id is the user id, so there is at most one row per user by construction. Only this side maps the
 * relation; an inverse {@code @OneToOne} on User could not be lazy and would cost a query on every
 * user load.
 */
@Entity
@Table(name = "user_ai_usage")
public class UserAiUsage {

    @Id
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private long calls;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "cost_usd", nullable = false)
    private double costUsd;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected UserAiUsage() {
    }

    public UserAiUsage(User user) {
        this.user = user;
    }

    public void add(long input, long output, double cost, Instant at) {
        calls++;
        inputTokens += input;
        outputTokens += output;
        costUsd += cost;
        lastUsedAt = at;
    }

    public Long getUserId() {
        return userId;
    }

    public User getUser() {
        return user;
    }

    public long getCalls() {
        return calls;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public double getCostUsd() {
        return costUsd;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
