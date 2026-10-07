package com.team5.advisor.web;

import com.team5.advisor.chat.AdvisorChatService;
import com.team5.advisor.web.ChatDtos.AdvisorChatRequest;
import com.team5.advisor.web.ChatDtos.AdvisorChatResponse;
import com.team5.auth.AuthenticatedAccountId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdvisorController {

    private final AdvisorChatService chatService;

    public AdvisorController(AdvisorChatService chatService) {
        this.chatService = chatService;
    }

    /** The account comes from the token only, never from the request, so a user can only ask about their own. */
    @PostMapping("/api/v1/advisor/chat")
    public AdvisorChatResponse chat(
        @Valid @RequestBody AdvisorChatRequest request,
        @AuthenticatedAccountId long accountId
    ) {
        return chatService.chat(accountId, request.messages());
    }
}
