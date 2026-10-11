package com.handyai.build.controller;

import com.handyai.build.dto.ToolResponse;
import com.handyai.build.security.CurrentUserProvider;
import com.handyai.build.service.FavoriteService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final CurrentUserProvider currentUserProvider;

    public FavoriteController(FavoriteService favoriteService,
                              CurrentUserProvider currentUserProvider) {
        this.favoriteService = favoriteService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<ToolResponse> myFavorites() {
        return favoriteService.listForUser(currentUserProvider.requireUserId());
    }
}
