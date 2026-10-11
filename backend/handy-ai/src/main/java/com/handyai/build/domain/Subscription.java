package com.handyai.build.domain;

import com.handyai.build.domain.PriceBook.BillingCycle;
import com.handyai.build.domain.PriceBook.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A plan someone pays for and tracks through HandyAI. HandyAI does not bill anyone: the purchase
 * happens on the vendor's site, and this row is the user's own record of it.
 */
@Entity
@Table(name = "subscriptions", uniqueConstraints = @UniqueConstraint(
        name = "uk_subscription_user_tool", columnNames = {"user_id", "tool_id"}))
public class Subscription {

    public enum Status {
        ACTIVE,
        CANCELLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tool_id", nullable = false)
    private AiTool tool;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 20)
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency = Currency.INR;

    /** What the user actually pays per billing cycle, in {@link #currency}. */
    @Column(nullable = false)
    private double amount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(length = 300)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** The first renewal on or after {@code today}; none once the plan is cancelled. */
    public LocalDate nextRenewal(LocalDate today) {
        if (status != Status.ACTIVE) {
            return null;
        }
        LocalDate next = startDate.plusMonths(billingCycle.months());
        int cycles = 1;
        while (next.isBefore(today)) {
            cycles++;
            next = startDate.plusMonths((long) billingCycle.months() * cycles);
        }
        return next;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public AiTool getTool() {
        return tool;
    }

    public void setTool(AiTool tool) {
        this.tool = tool;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public void setBillingCycle(BillingCycle billingCycle) {
        this.billingCycle = billingCycle;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
