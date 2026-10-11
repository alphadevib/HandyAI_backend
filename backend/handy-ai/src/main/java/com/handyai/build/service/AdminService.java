package com.handyai.build.service;

import com.handyai.build.domain.AccountType;
import com.handyai.build.domain.User;
import com.handyai.build.domain.VerificationStatus;
import com.handyai.build.dto.UserResponse;
import com.handyai.build.exception.BadRequestException;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The manual half of organisation verification: an admin approves or rejects each request. */
@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** {@code status} is PENDING, VERIFIED, REJECTED, or blank for every organisation. */
    @Transactional(readOnly = true)
    public List<UserResponse> organisations(String status) {
        List<User> users = status == null || status.isBlank()
                ? userRepository.findByAccountTypeOrderByVerificationSubmittedAtDesc(
                        AccountType.ORGANISATION)
                : userRepository.findByAccountTypeAndVerificationStatusOrderByVerificationSubmittedAtAsc(
                        AccountType.ORGANISATION, parseStatus(status));
        return users.stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse approve(Long userId) {
        User user = loadOrganisation(userId);
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setVerificationNote(null);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse reject(Long userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Give the organisation a reason so it can fix the request");
        }
        User user = loadOrganisation(userId);
        user.setVerificationStatus(VerificationStatus.REJECTED);
        user.setVerificationNote(reason.trim());
        return UserResponse.from(user);
    }

    private User loadOrganisation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        if (user.getAccountType() != AccountType.ORGANISATION) {
            throw new BadRequestException("Only organisation accounts need verification");
        }
        return user;
    }

    private VerificationStatus parseStatus(String status) {
        try {
            return VerificationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown verification status: " + status);
        }
    }
}
