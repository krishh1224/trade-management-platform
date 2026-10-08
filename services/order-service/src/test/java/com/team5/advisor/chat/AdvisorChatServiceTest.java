package com.team5.advisor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team5.advisor.data.AdvisorRepository;
import com.team5.advisor.data.AdvisorRepository.AccountRow;
import com.team5.advisor.tools.AdvisorTools;
import com.team5.advisor.web.ChatDtos.AdvisorChatResponse;
import com.team5.advisor.web.ChatDtos.ChatMessage;
import com.team5.advisor.web.ChatDtos.ChatRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

class AdvisorChatServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-04T10:00:00Z"), ZoneId.of("Asia/Kolkata"));

    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final AdvisorRepository repository = mock(AdvisorRepository.class);
    private final AdvisorChatService service = new AdvisorChatService(chatClient, repository,
        new AdvisorTools(repository), "Today is %TODAY%. Cash in %BASE_CURRENCY%.", "gemini-2.5-flash", CLOCK);

    private static List<ChatMessage> ask(String question) {
        return List.of(new ChatMessage(ChatRole.USER, question));
    }

    private void modelReplies(String reply) {
        when(chatClient.prompt().system(anyString()).messages(anyList()).user(anyString())
            .tools(any(Object[].class)).toolContext(anyMap()).call().content()).thenReturn(reply);
    }

    @Test
    void lastMessageMustComeFromTheUser() {
        List<ChatMessage> messages = List.of(
            new ChatMessage(ChatRole.USER, "hi"),
            new ChatMessage(ChatRole.ASSISTANT, "hello"));

        assertThatThrownBy(() -> service.chat(103L, messages))
            .isInstanceOf(AdvisorException.class)
            .extracting("errorCode").isEqualTo("VAL-422");
        verifyNoInteractions(repository);
    }

    @Test
    void unknownAccountIsRejectedBeforeCallingTheModel() {
        when(repository.findAccount(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.chat(999L, ask("hi")))
            .extracting("errorCode").isEqualTo("ACC-404");
    }

    @Test
    void suspendedAccountIsRejectedBeforeCallingTheModel() {
        when(repository.findAccount(105L))
            .thenReturn(Optional.of(new AccountRow(105L, "INR", BigDecimal.TEN, "SUSPENDED")));

        assertThatThrownBy(() -> service.chat(105L, ask("hi")))
            .extracting("errorCode").isEqualTo("ACC-403");
    }

    @Test
    void returnsTheModelReplyWithModelAndUtcTimestamp() {
        when(repository.findAccount(103L))
            .thenReturn(Optional.of(new AccountRow(103L, "INR", BigDecimal.TEN, "ACTIVE")));
        modelReplies("  Your largest holding is ITC.NS.  ");

        AdvisorChatResponse response = service.chat(103L, ask("What is my largest holding?"));

        assertThat(response.reply()).isEqualTo("Your largest holding is ITC.NS.");
        assertThat(response.model()).isEqualTo("gemini-2.5-flash");
        assertThat(response.generatedAt().toInstant()).isEqualTo(Instant.parse("2026-10-04T10:00:00Z"));
        assertThat(response.toolsUsed()).isEmpty();
    }

    @Test
    void emptyModelReplyIsReportedAsUnavailable() {
        when(repository.findAccount(103L))
            .thenReturn(Optional.of(new AccountRow(103L, "INR", BigDecimal.TEN, "ACTIVE")));
        modelReplies("   ");

        assertThatThrownBy(() -> service.chat(103L, ask("hi")))
            .extracting("errorCode").isEqualTo("ADV-503");
    }

    @Test
    void systemPromptCarriesTodayAndTheBaseCurrency() {
        assertThat(service.systemPrompt("INR")).isEqualTo("Today is Sunday 4 October 2026. Cash in INR.");
    }

    @Test
    void providerRateLimitMapsToAdv429AndAnythingElseToAdv503() {
        RuntimeException rateLimited = new RuntimeException("call failed",
            new IllegalStateException("429 RESOURCE_EXHAUSTED: quota exceeded"));

        assertThat(AdvisorException.fromProviderFailure(rateLimited).getErrorCode()).isEqualTo("ADV-429");
        assertThat(AdvisorException.fromProviderFailure(new RuntimeException("API key not valid"))
            .getErrorCode()).isEqualTo("ADV-503");
    }
}
