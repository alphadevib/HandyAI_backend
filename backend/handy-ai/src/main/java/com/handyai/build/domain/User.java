package com.handyai.build.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    /**
     * The person's profession, or an organisation's industry. Usually one of the names in
     * {@link ProfessionCatalog}; anything else is still used as free text by the recommender.
     */
    @Column(name = "profession", length = 120)
    private String profession;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20)
    private AccountType accountType = AccountType.INDIVIDUAL;

    @Column(name = "organisation_name", length = 160)
    private String organisationName;

    @Column(name = "organisation_website", length = 200)
    private String organisationWebsite;

    /** GSTIN, CIN or LLPIN, stored upper-case without spaces. */
    @Column(name = "organisation_registration_id", length = 40)
    private String organisationRegistrationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.NOT_REQUIRED;

    /** Why a request was rejected, shown to the organisation so it can fix and resubmit. */
    @Column(name = "verification_note", length = 300)
    private String verificationNote;

    @Column(name = "verification_submitted_at")
    private Instant verificationSubmittedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    /** Rows created before account types existed read as individuals. */
    public AccountType getAccountType() {
        return accountType == null ? AccountType.INDIVIDUAL : accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public String getOrganisationName() {
        return organisationName;
    }

    public void setOrganisationName(String organisationName) {
        this.organisationName = organisationName;
    }

    public String getOrganisationWebsite() {
        return organisationWebsite;
    }

    public void setOrganisationWebsite(String organisationWebsite) {
        this.organisationWebsite = organisationWebsite;
    }

    public String getOrganisationRegistrationId() {
        return organisationRegistrationId;
    }

    public void setOrganisationRegistrationId(String organisationRegistrationId) {
        this.organisationRegistrationId = organisationRegistrationId;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus == null ? VerificationStatus.NOT_REQUIRED : verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getVerificationNote() {
        return verificationNote;
    }

    public void setVerificationNote(String verificationNote) {
        this.verificationNote = verificationNote;
    }

    public Instant getVerificationSubmittedAt() {
        return verificationSubmittedAt;
    }

    public void setVerificationSubmittedAt(Instant verificationSubmittedAt) {
        this.verificationSubmittedAt = verificationSubmittedAt;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
