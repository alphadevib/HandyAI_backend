package com.handyai.build.controller;

import com.handyai.build.dto.SubscriptionRequest;
import com.handyai.build.dto.SubscriptionResponse;
import com.handyai.build.dto.SubscriptionResponse.Overview;
import com.handyai.build.security.CurrentUserProvider;
import com.handyai.build.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final CurrentUserProvider currentUserProvider;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  CurrentUserProvider currentUserProvider) {
        this.subscriptionService = subscriptionService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public Overview list() {
        return subscriptionService.overview(currentUserProvider.requireUserId());
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> add(@Valid @RequestBody SubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.add(currentUserProvider.requireUserId(), request));
    }

    @PutMapping("/{id}")
    public SubscriptionResponse update(@PathVariable Long id,
                                       @Valid @RequestBody SubscriptionRequest request) {
        return subscriptionService.update(currentUserProvider.requireUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> remove(@PathVariable Long id) {
        subscriptionService.remove(currentUserProvider.requireUserId(), id);
        return Map.of("deleted", true);
    }
}
