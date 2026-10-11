package com.handyai.build.config;

import com.handyai.build.domain.Role;
import com.handyai.build.domain.User;
import com.handyai.build.repository.UserRepository;
import com.handyai.build.security.PasswordHasher;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes sure exactly one admin exists: the configured email.
 *
 * <p>On every start the account is created if missing, promoted if it signed up as a normal user,
 * and given the configured password if that changed. Any other account holding the admin role is
 * demoted, so the role cannot linger on an old or test account. The password comes from
 * configuration (an environment variable or a git-ignored file), never from source code.
 */
@Component
@Order(0)
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountInitializer(UserRepository userRepository, PasswordHasher passwordHasher,
                                   @Value("${handyai.admin.email}") String adminEmail,
                                   @Value("${handyai.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.adminEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (User user : userRepository.findByRole(Role.ADMIN)) {
            if (!user.getEmail().equalsIgnoreCase(adminEmail)) {
                user.setRole(Role.USER);
                log.warn("Removed the admin role from {}: only {} may be admin", user.getEmail(),
                        adminEmail);
            }
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("No admin password configured (HANDYAI_ADMIN_PASSWORD); the admin account "
                    + "for {} was not created or updated", adminEmail);
            return;
        }
        if (adminPassword.length() < 8) {
            throw new IllegalStateException("The admin password must be at least 8 characters");
        }

        User admin = userRepository.findByEmailIgnoreCase(adminEmail).orElseGet(() -> {
            User created = new User();
            created.setName("Syed Ibrahim Ahmed");
            created.setEmail(adminEmail);
            created.setProfession("Entrepreneur / founder");
            log.info("Created the admin account {}", adminEmail);
            return created;
        });
        admin.setRole(Role.ADMIN);
        if (admin.getPasswordHash() == null
                || !passwordHasher.matches(adminPassword, admin.getPasswordHash())) {
            admin.setPasswordHash(passwordHasher.hash(adminPassword));
        }
        userRepository.save(admin);
    }
}
