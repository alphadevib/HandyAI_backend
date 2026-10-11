package com.handyai.build.controller;

import com.handyai.build.domain.AiTool;
import com.handyai.build.domain.OutboundClick;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.repository.AiToolRepository;
import com.handyai.build.repository.OutboundClickRepository;
import com.handyai.build.security.CurrentUserProvider;
import com.handyai.build.service.PresenceService;
import jakarta.validation.Valid;
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
@RequestMapping("/api")
public class TrackingController {

    private static final Set<String> SOURCES = Set.of("card", "detail", "chat");

    private final PresenceService presenceService;
    private final AiToolRepository toolRepository;
    private final OutboundClickRepository clickRepository;
    private final CurrentUserProvider currentUserProvider;

    public TrackingController(PresenceService presenceService, AiToolRepository toolRepository,
                              OutboundClickRepository clickRepository,
                              CurrentUserProvider currentUserProvider) {
        this.presenceService = presenceService;
        this.toolRepository = toolRepository;
        this.clickRepository = clickRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/presence")
    public Map<String, Object> heartbeat(@Valid @RequestBody Heartbeat heartbeat) {
        boolean ok = presenceService.heartbeat(heartbeat.visitorId(),
                currentUserProvider.currentUserId().orElse(null), heartbeat.path());
        return Map.of("ok", ok);
    }

    /**
     * Records the hand-off and sends the browser on to the vendor. The destination is always the
     * tool's own stored website, never a URL from the request, so this cannot be used as an open
     * redirect.
     */
    @GetMapping("/go/{slug}")
    public ResponseEntity<Void> go(@PathVariable String slug,
                                   @RequestParam(name = "src", required = false) String source,
                                   @RequestParam(name = "v", required = false) String visitorId) {
        AiTool tool = toolRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Tool", slug));
        OutboundClick click = new OutboundClick();
        click.setTool(tool);
        click.setSource(source != null && SOURCES.contains(source) ? source : "card");
        click.setUserId(presenceService.userFor(visitorId));
        clickRepository.save(click);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(tool.getWebsiteUrl()))
                .header("Cache-Control", "no-store")
                .build();
    }

    public record Heartbeat(@Size(max = 64) String visitorId, @Size(max = 120) String path) {
    }
}
