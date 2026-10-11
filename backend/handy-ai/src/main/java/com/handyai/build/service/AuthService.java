package com.handyai.build.service;

import com.handyai.build.domain.AccountType;
import com.handyai.build.domain.Role;
import com.handyai.build.domain.User;
import com.handyai.build.domain.VerificationStatus;
import com.handyai.build.exception.BadRequestException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import com.handyai.build.dto.AuthResponse;
import com.handyai.build.dto.LoginRequest;
import com.handyai.build.dto.RegisterRequest;
import com.handyai.build.dto.UserResponse;
import com.handyai.build.exception.ConflictException;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.exception.UnauthorizedException;
import com.handyai.build.repository.UserRepository;
import com.handyai.build.exception.TooManyRequestsException;
import com.handyai.build.security.JwtService;
import com.handyai.build.security.LoginThrottle;
import com.handyai.build.security.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final OrganisationVerifier organisationVerifier;
    private final LoginThrottle loginThrottle;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher,
                       JwtService jwtService, OrganisationVerifier organisationVerifier,
                       LoginThrottle loginThrottle) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.organisationVerifier = organisationVerifier;
        this.loginThrottle = loginThrottle;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordHasher.hash(request.password()));
        user.setProfession(trimToNull(request.profession()));
        user.setRole(Role.USER);

        AccountType accountType = parseAccountType(request.accountType());
        user.setAccountType(accountType);
        if (accountType == AccountType.ORGANISATION) {
            applyChecked(user, organisationVerifier.check(email, request.organisationName(),
                    request.organisationWebsite(), request.organisationRegistrationId()));
        } else {
            user.setVerificationStatus(VerificationStatus.NOT_REQUIRED);
        }

        User saved = userRepository.save(user);
        return tokenFor(saved);
    }

    /**
     * Hashing runs outside any database write, and the lookup is read-only, so a burst of logins
     * costs CPU but never holds a row lock.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normaliseEmail(request.email());
        long locked = loginThrottle.minutesLocked(email);
        if (locked > 0) {
            throw new TooManyRequestsException("Too many wrong passwords. Try again in " + locked
                    + (locked == 1 ? " minute." : " minutes."));
        }
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !passwordHasher.matches(request.password(), user.getPasswordHash())) {
            // Unknown emails count too, so the lockout does not reveal which accounts exist.
            loginThrottle.recordFailure(email);
            throw new UnauthorizedException("Email or password is incorrect");
        }
        loginThrottle.recordSuccess(email);
        return tokenFor(user);
    }

    @Transactional(readOnly = true)
    public UserResponse profile(Long userId) {
        return UserResponse.from(loadUser(userId));
    }

    /**
     * Only the fields sent are changed. For an organisation, editing the name, website or
     * registration number (or resubmitting after a rejection) sends the account back for review.
     */
    @Transactional
    public UserResponse updateProfile(Long userId, String name, String profession,
                                      String organisationName, String organisationWebsite,
                                      String organisationRegistrationId) {
        User user = loadUser(userId);
        if (name != null && !name.isBlank()) {
            user.setName(name.trim());
        }
        if (profession != null) {
            if (profession.isBlank()) {
                throw new BadRequestException("Choose a profession so we can suggest the right tools",
                        Map.of("profession", "Choose a profession"));
            }
            user.setProfession(profession.trim());
        }
        boolean organisationEdited = organisationName != null || organisationWebsite != null
                || organisationRegistrationId != null;
        if (user.getAccountType() == AccountType.ORGANISATION && organisationEdited) {
            OrganisationVerifier.Checked checked = organisationVerifier.check(user.getEmail(),
                    organisationName != null ? organisationName : user.getOrganisationName(),
                    organisationWebsite != null ? organisationWebsite : user.getOrganisationWebsite(),
                    organisationRegistrationId != null ? organisationRegistrationId
                            : user.getOrganisationRegistrationId());
            boolean changed = !checked.name().equals(user.getOrganisationName())
                    || !checked.website().equals(user.getOrganisationWebsite())
                    || !checked.registrationId().equals(user.getOrganisationRegistrationId());
            if (changed || user.getVerificationStatus() == VerificationStatus.REJECTED) {
                applyChecked(user, checked);
            }
        }
        return UserResponse.from(user);
    }

    private void applyChecked(User user, OrganisationVerifier.Checked checked) {
        user.setOrganisationName(checked.name());
        user.setOrganisationWebsite(checked.website());
        user.setOrganisationRegistrationId(checked.registrationId());
        user.setVerificationStatus(VerificationStatus.PENDING);
        user.setVerificationNote(null);
        user.setVerificationSubmittedAt(Instant.now());
    }

    private AccountType parseAccountType(String value) {
        if (value == null || value.isBlank()) {
            return AccountType.INDIVIDUAL;
        }
        try {
            return AccountType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Account type must be INDIVIDUAL or ORGANISATION");
        }
    }

    @Transactional(readOnly = true)
    public User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    private AuthResponse tokenFor(User user) {
        String token = jwtService.issue(user.getId(), user.getEmail(), user.getRole().name());
        return new AuthResponse(token, jwtService.getTtl().toSeconds(), UserResponse.from(user));
    }

    private String normaliseEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
