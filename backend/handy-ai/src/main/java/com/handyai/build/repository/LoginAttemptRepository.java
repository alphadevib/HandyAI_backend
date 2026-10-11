package com.handyai.build.repository;

import com.handyai.build.domain.LoginAttempt;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {

    @Modifying
    @Query("delete from LoginAttempt a where a.firstFailure < :cutoff")
    int deleteStartedBefore(@Param("cutoff") Instant cutoff);
}
