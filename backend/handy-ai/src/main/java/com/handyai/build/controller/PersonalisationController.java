package com.handyai.build.controller;

import com.handyai.build.dto.CategoryResponse;
import com.handyai.build.dto.ToolResponse;
import com.handyai.build.security.CurrentUserProvider;
import com.handyai.build.service.PersonalisationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's interests, their "for you" feed and their AI usage. */
@RestController
@Tag(name = "Personalisation", description = "Followed categories, the \"for you\" feed and the user's AI usage.")
@RequestMapping("/api/me")
public class PersonalisationController {

    private final PersonalisationService personalisationService;
    private final CurrentUserProvider currentUserProvider;

    public PersonalisationController(PersonalisationService personalisationService,
                                     CurrentUserProvider currentUserProvider) {
        this.personalisationService = personalisationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/interests")
    public List<CategoryResponse> interests() {
        return personalisationService.interests(currentUserProvider.requireUserId());
    }

    @PutMapping("/interests")
    public List<CategoryResponse> setInterests(@Valid @RequestBody InterestsRequest request) {
        return personalisationService.setInterests(currentUserProvider.requireUserId(),
                request.slugs());
    }

    @GetMapping("/for-you")
    public List<ToolResponse> forYou(@RequestParam(defaultValue = "12") int limit) {
        return personalisationService.forYou(currentUserProvider.requireUserId(), limit);
    }

    @GetMapping("/ai-usage")
    public PersonalisationService.AiUsageView aiUsage() {
        return personalisationService.aiUsage(currentUserProvider.requireUserId());
    }

    public record InterestsRequest(
            @NotNull(message = "Send the category slugs to follow, or an empty list")
            @Size(max = 20, message = "Follow at most 20 categories")
            List<@NotBlank String> slugs) {
    }
}
