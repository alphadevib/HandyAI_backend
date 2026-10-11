package com.handyai.build.service;

import com.handyai.build.domain.AiTool;
import com.handyai.build.domain.Favorite;
import com.handyai.build.domain.User;
import com.handyai.build.dto.ToolResponse;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.repository.AiToolRepository;
import com.handyai.build.repository.FavoriteRepository;
import com.handyai.build.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final AiToolRepository toolRepository;
    private final UserRepository userRepository;

    public FavoriteService(FavoriteRepository favoriteRepository, AiToolRepository toolRepository,
                           UserRepository userRepository) {
        this.favoriteRepository = favoriteRepository;
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
    }

    /** Returns the new state: {@code true} when the tool is now saved for the user. */
    @Transactional
    public boolean toggle(Long userId, String toolSlug) {
        AiTool tool = requireTool(toolSlug);
        var existing = favoriteRepository.findByUserIdAndToolId(userId, tool.getId());
        if (existing.isPresent()) {
            favoriteRepository.delete(existing.get());
            return false;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
        Favorite favorite = new Favorite();
        favorite.setUser(user);
        favorite.setTool(tool);
        favoriteRepository.save(favorite);
        return true;
    }

    /**
     * Loads the saved tools in one {@code in (...)} query and re-orders them in memory, so a long
     * list of favourites still costs exactly two queries.
     */
    @Transactional(readOnly = true)
    public List<ToolResponse> listForUser(Long userId) {
        List<Long> orderedIds = favoriteRepository.findToolIdsByUser(userId);
        if (orderedIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AiTool> byId = toolRepository.findByIdIn(orderedIds).stream()
                .collect(Collectors.toMap(AiTool::getId, Function.identity()));
        List<ToolResponse> ordered = new ArrayList<>(orderedIds.size());
        for (Long id : orderedIds) {
            AiTool tool = byId.get(id);
            if (tool != null) {
                ordered.add(ToolResponse.from(tool, true));
            }
        }
        return ordered;
    }

    private AiTool requireTool(String slug) {
        return toolRepository.findBySlug(slug)
                .orElseThrow(() -> ResourceNotFoundException.of("Tool", slug));
    }
}
