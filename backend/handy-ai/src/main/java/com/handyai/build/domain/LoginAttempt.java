package com.handyai.build.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Failed sign-ins for one throttle key ("email|address", or "address|..." for the per-address cap)
 * since the first failure in the current window. Stored so a restart does not reset a lockout.
 */
@Entity
@Table(name = "login_throttle")
public class LoginAttempt {

    @Id
    @Column(name = "throttle_key", length = 220)
    private String key;

    @Column(nullable = false)
    private int failures;

    @Column(name = "first_failure", nullable = false)
    private Instant firstFailure;

    protected LoginAttempt() {
    }

    public LoginAttempt(String key, Instant firstFailure) {
        this.key = key;
        this.failures = 1;
        this.firstFailure = firstFailure;
    }

    public String getKey() {
        return key;
    }

    public int getFailures() {
        return failures;
    }

    public Instant getFirstFailure() {
        return firstFailure;
    }

    public void addFailure() {
        failures++;
    }
}
