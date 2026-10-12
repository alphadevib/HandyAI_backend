package com.handyai.build.controller;

import com.handyai.build.domain.AiTool;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.repository.AiToolRepository;
import com.handyai.build.security.CurrentUserProvider;
import com.handyai.build.service.ClickTracker;
import com.handyai.build.service.PresenceService;
import com.handyai.build.support.RecentKeys;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Live presence and the purchase hand-off to vendor sites. Both are open to guests. */
@RestController
@Tag(name = "Tracking", description = "Live-visitor heartbeats and outbound purchase redirects.")
@RequestMapping("/api")
public class TrackingController {

    private static final Set<String> SOURCES = Set.of("card", "detail", "chat");

    private final PresenceService presenceService;
    private final AiToolRepository toolRepository;
    private final ClickTracker clickTracker;
    private final CurrentUserProvider currentUserProvider;
    private final RecentKeys clickDeduper = new RecentKeys(50_000);
    static final Duration CLICK_WINDOW = Duration.ofMinutes(10);

    public TrackingController(PresenceService presenceService, AiToolRepository toolRepository,
                              ClickTracker clickTracker,
                              CurrentUserProvider currentUserProvider) {
        this.presenceService = presenceService;
        this.toolRepository = toolRepository;
        this.clickTracker = clickTracker;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/presence")
    public Map<String, Object> heartbeat(@Valid @RequestBody Heartbeat heartbeat,
                                         HttpServletRequest request) {
        boolean ok = presenceService.heartbeat(heartbeat.visitorId(),
                currentUserProvider.currentUserId().orElse(null), heartbeat.path(),
                request.getRemoteAddr());
        return Map.of("ok", ok);
    }

    /**
     * Records the hand-off (in the background, see {@link ClickTracker}) and sends the browser on
     * to the vendor. The destination is always the
     * tool's own stored website, never a URL from the request, so this cannot be used as an open
     * redirect.
     */
    @GetMapping("/go/{slug}")
    public ResponseEntity<Void> go(@PathVariable String slug,
                                   @RequestParam(name = "src", required = false) String source,
                                   @RequestParam(name = "v", required = false) String visitorId,
                                   HttpServletRequest request) {
        AiTool tool = toolRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Tool", slug));
        // Repeat clicks (or a script hammering the link) within the window count once, so the
        // admin numbers reflect people rather than requests. The redirect always happens.
        String who = (visitorId != null && !visitorId.isBlank() ? visitorId : request.getRemoteAddr())
                + "|" + tool.getId();
        if (!clickDeduper.firstIn(who, CLICK_WINDOW)) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(tool.getWebsiteUrl())).build();
        }
        clickTracker.record(tool.getId(), source != null && SOURCES.contains(source) ? source : "card",
                presenceService.userFor(visitorId));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(tool.getWebsiteUrl()))
                .header("Cache-Control", "no-store")
                .build();
    }

    public record Heartbeat(@Size(max = 64) String visitorId, @Size(max = 120) String path) {
    }
}
