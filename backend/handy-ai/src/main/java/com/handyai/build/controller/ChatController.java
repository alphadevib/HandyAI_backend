package com.handyai.build.controller;

import com.handyai.build.dto.ChatRequest;
import com.handyai.build.dto.ChatResponse;
import com.handyai.build.security.CurrentUserProvider;
import com.handyai.build.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final CurrentUserProvider currentUserProvider;

    public ChatController(ChatService chatService, CurrentUserProvider currentUserProvider) {
        this.chatService = chatService;
        this.currentUserProvider = currentUserProvider;
    }

    /** Open to guests; a signed-in caller gets answers shaped by their profession. */
    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.reply(request, currentUserProvider.currentUserId().orElse(null));
    }
}
