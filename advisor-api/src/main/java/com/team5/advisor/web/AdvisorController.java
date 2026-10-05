package com.team5.advisor.web;

import com.team5.advisor.auth.AccountClaims;
import com.team5.advisor.chat.AdvisorChatService;
import com.team5.advisor.web.ChatDtos.AdvisorChatRequest;
import com.team5.advisor.web.ChatDtos.AdvisorChatResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdvisorController {

    private final AdvisorChatService chatService;

    public AdvisorController(AdvisorChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/api/v1/advisor/chat")
    public AdvisorChatResponse chat(
        @Valid @RequestBody AdvisorChatRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        return chatService.chat(AccountClaims.accountId(jwt), request.messages());
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
