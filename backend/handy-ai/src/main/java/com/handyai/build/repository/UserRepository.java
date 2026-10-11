package com.handyai.build.repository;

import com.handyai.build.domain.AccountType;
import com.handyai.build.domain.Role;
import java.time.Instant;
import com.handyai.build.domain.User;
import com.handyai.build.domain.VerificationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByAccountTypeAndVerificationStatusOrderByVerificationSubmittedAtAsc(
            AccountType accountType, VerificationStatus status);

    List<User> findByAccountTypeOrderByVerificationSubmittedAtDesc(AccountType accountType);

    List<User> findByRole(Role role);

    long countByCreatedAtAfter(Instant since);

    long countByAccountType(AccountType accountType);

    List<User> findTop8ByOrderByCreatedAtDesc();
}
