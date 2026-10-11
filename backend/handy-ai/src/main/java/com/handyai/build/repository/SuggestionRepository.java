package com.handyai.build.repository;

import com.handyai.build.domain.Suggestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    List<Suggestion> findTop200ByOrderByCreatedAtDesc();

    List<Suggestion> findTop200ByStatusOrderByCreatedAtDesc(Suggestion.Status status);

    long countByStatus(Suggestion.Status status);
}
