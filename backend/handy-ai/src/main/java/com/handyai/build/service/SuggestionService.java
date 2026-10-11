package com.handyai.build.service;

import com.handyai.build.domain.Suggestion;
import com.handyai.build.exception.BadRequestException;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.exception.TooManyRequestsException;
import com.handyai.build.repository.SuggestionRepository;
import com.handyai.build.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suggestions in from anyone, out only to the admin. Each sender (account, or network address for
 * guests) may send {@link #MAX_PER_WINDOW} in {@link #WINDOW}, which stops the box being flooded.
 */
@Service
public class SuggestionService {

    static final int MAX_PER_WINDOW = 5;
    static final Duration WINDOW = Duration.ofMinutes(10);

    public record SuggestionView(Long id, String category, String message, String page,
                                 String status, Instant createdAt, String name, String email,
                                 Long userId, String memberName, String memberEmail) {
    }

    private final SuggestionRepository suggestionRepository;
    private final UserRepository userRepository;
    private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

    public SuggestionService(SuggestionRepository suggestionRepository,
                             UserRepository userRepository) {
        this.suggestionRepository = suggestionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void submit(String category, String message, String name, String email, String page,
                       Long userId, String senderKey) {
        String text = message == null ? "" : message.trim();
        if (text.length() < 5) {
            throw new BadRequestException("Tell us a little more: at least a few words",
                    Map.of("message", "Write at least a few words"));
        }
        throttle(userId != null ? "user:" + userId : "addr:" + senderKey);

        Suggestion suggestion = new Suggestion();
        suggestion.setCategory(parseCategory(category));
        suggestion.setMessage(text);
        suggestion.setName(trimToNull(name));
        suggestion.setEmail(trimToNull(email));
        suggestion.setPage(trimToNull(page));
        suggestion.setUserId(userId);
        suggestionRepository.save(suggestion);
    }

    @Transactional(readOnly = true)
    public List<SuggestionView> list(String status) {
        List<Suggestion> rows = status == null || status.isBlank()
                ? suggestionRepository.findTop200ByOrderByCreatedAtDesc()
                : suggestionRepository.findTop200ByStatusOrderByCreatedAtDesc(parseStatus(status));
        return rows.stream().map(this::view).toList();
    }

    @Transactional
    public SuggestionView updateStatus(Long id, String status) {
        Suggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Suggestion", id));
        suggestion.setStatus(parseStatus(status));
        return view(suggestion);
    }

    public long countNew() {
        return suggestionRepository.countByStatus(Suggestion.Status.NEW);
    }

    private SuggestionView view(Suggestion s) {
        var member = s.getUserId() == null ? null : userRepository.findById(s.getUserId()).orElse(null);
        return new SuggestionView(s.getId(), s.getCategory().name(), s.getMessage(), s.getPage(),
                s.getStatus().name(), s.getCreatedAt(), s.getName(), s.getEmail(), s.getUserId(),
                member == null ? null : member.getName(), member == null ? null : member.getEmail());
    }

    private void throttle(String key) {
        Instant now = Instant.now();
        Deque<Instant> times = recent.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(WINDOW))) {
                times.pollFirst();
            }
            if (times.size() >= MAX_PER_WINDOW) {
                throw new TooManyRequestsException(
                        "Thanks for all the ideas! Please wait a few minutes before sending more.");
            }
            times.addLast(now);
        }
        if (recent.size() > 20_000) {
            recent.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }

    private Suggestion.Category parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return Suggestion.Category.IDEA;
        }
        try {
            return Suggestion.Category.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown suggestion type: " + value);
        }
    }

    private Suggestion.Status parseStatus(String value) {
        try {
            return Suggestion.Status.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new BadRequestException("Status must be NEW, PLANNED, DONE or DISMISSED");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
