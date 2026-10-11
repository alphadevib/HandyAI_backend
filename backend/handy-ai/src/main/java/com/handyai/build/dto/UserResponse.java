package com.handyai.build.dto;

import com.handyai.build.domain.User;

public record UserResponse(Long id, String name, String email, String profession, String role,
                           String accountType, String organisationName,
                           String organisationWebsite, String organisationRegistrationId,
                           String verificationStatus, String verificationNote) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getProfession(), user.getRole().name(), user.getAccountType().name(),
                user.getOrganisationName(), user.getOrganisationWebsite(),
                user.getOrganisationRegistrationId(), user.getVerificationStatus().name(),
                user.getVerificationNote());
    }
}
