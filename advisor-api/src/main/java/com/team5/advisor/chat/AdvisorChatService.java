package com.team5.advisor.chat;

import com.team5.advisor.data.AdvisorRepository;
import com.team5.advisor.data.AdvisorRepository.AccountRow;
import com.team5.advisor.tools.AdvisorTools;
import com.team5.advisor.tools.ToolCallTracker;
import com.team5.advisor.web.ChatDtos.AdvisorChatResponse;
import com.team5.advisor.web.ChatDtos.ChatMessage;
import com.team5.advisor.web.ChatDtos.ChatRole;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * One stateless chat turn: checks the account, sends the conversation to the model with the
 * read-only tools bound to the caller's account, and returns the reply with the tools it used.
 */
@Service
public class AdvisorChatService {

    private static final Logger log = LoggerFactory.getLogger(AdvisorChatService.class);
    private static final DateTimeFormatter TODAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);

    private final ChatClient chatClient;
    private final AdvisorRepository repository;
    private final AdvisorTools tools;
    private final String systemPromptTemplate;
    private final String model;
    private final Clock clock;

    @Autowired
    public AdvisorChatService(
        ChatClient.Builder chatClientBuilder,
        AdvisorRepository repository,
        AdvisorTools tools,
        @Value("classpath:prompts/advisor-system.md") Resource systemPrompt,
        @Value("${spring.ai.google.genai.chat.model}") String model
    ) {
        this(chatClientBuilder.build(), repository, tools, read(systemPrompt), model, Clock.systemDefaultZone());
    }

    AdvisorChatService(ChatClient chatClient, AdvisorRepository repository, AdvisorTools tools,
                       String systemPromptTemplate, String model, Clock clock) {
        this.chatClient = chatClient;
        this.repository = repository;
        this.tools = tools;
        this.systemPromptTemplate = systemPromptTemplate;
        this.model = model;
        this.clock = clock;
    }

    public AdvisorChatResponse chat(long accountId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty() || messages.getLast().role() != ChatRole.USER) {
            throw AdvisorException.invalidInput();
        }

        AccountRow account = repository.findAccount(accountId).orElseThrow(AdvisorException::accountNotFound);
        if (!"ACTIVE".equals(account.status())) {
            throw AdvisorException.accountNotActive();
        }

        List<Message> history = new ArrayList<>();
        for (ChatMessage message : messages.subList(0, messages.size() - 1)) {
            history.add(message.role() == ChatRole.USER
                ? new UserMessage(message.content())
                : new AssistantMessage(message.content()));
        }
        String question = messages.getLast().content();

        ToolCallTracker tracker = new ToolCallTracker();
        String reply;
        try {
            reply = chatClient.prompt()
                .system(systemPrompt(account.baseCurrency()))
                .messages(history)
                .user(question)
                .tools(tools)
                .toolContext(Map.of(
                    AdvisorTools.CTX_ACCOUNT_ID, accountId,
                    AdvisorTools.CTX_BASE_CURRENCY, account.baseCurrency(),
                    AdvisorTools.CTX_TRACKER, tracker
                ))
                .call()
                .content();
        } catch (AdvisorException e) {
            throw e;
        } catch (RuntimeException e) {
            AdvisorException mapped = AdvisorException.fromProviderFailure(e);
            log.warn("Advisor model call failed for account {} ({}): {} | root cause: {}", accountId,
                mapped.getErrorCode(), e.getMessage(), rootCauseMessage(e));
            throw mapped;
        }

        if (reply == null || reply.isBlank()) {
            log.warn("Advisor model returned an empty reply for account {}", accountId);
            throw AdvisorException.unavailable();
        }
        return new AdvisorChatResponse(reply.strip(), tracker.names(), model,
            OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC));
    }

    String systemPrompt(String baseCurrency) {
        return systemPromptTemplate
            .replace("%TODAY%", LocalDate.now(clock).format(TODAY_FORMAT))
            .replace("%BASE_CURRENCY%", baseCurrency);
    }

    /** The innermost cause's message (e.g. the provider's own error), which wrapper exceptions hide. */
    static String rootCauseMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the advisor system prompt", e);
        }
    }
}
