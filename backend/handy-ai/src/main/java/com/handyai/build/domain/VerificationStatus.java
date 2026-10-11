package com.handyai.build.domain;

/** Where an organisation account is in review. Individuals are always {@link #NOT_REQUIRED}. */
public enum VerificationStatus {
    NOT_REQUIRED,
    PENDING,
    VERIFIED,
    REJECTED
}
