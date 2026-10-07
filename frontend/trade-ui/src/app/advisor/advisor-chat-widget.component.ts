import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  ADVISOR_MAX_MESSAGE_LENGTH,
  AdvisorChatMessage,
  AdvisorService
} from '../api/advisor.service';
import { toUserFacingErrorMessage } from '../api/error-catalogue';
import { AuthSessionService } from '../auth/auth-session.service';
import { renderAdvisorMarkdown } from './advisor-markdown';

interface ConversationEntry extends AdvisorChatMessage {
  html: string;
  toolsUsed?: string[];
}

const TOOL_LABELS: Readonly<Record<string, string>> = {
  get_portfolio: 'your portfolio',
  get_quotes: 'stored prices',
  get_fx_rate: 'FX rates',
  search_instruments: 'instrument search',
  get_order_history: 'your orders',
  get_advice_signals: 'your current platform signals'
};

const ADVISOR_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  'ADV-429': 'The advisor is busy right now. Wait a minute and try again.',
  'ADV-503': 'The advisor is unavailable right now. Please try again later.'
};

const STORAGE_KEY = 'trade-ui.advisor.conversation';

@Component({
  selector: 'app-advisor-chat-widget',
  imports: [FormsModule],
  templateUrl: './advisor-chat-widget.component.html',
  styleUrl: './advisor-chat-widget.component.css'
})
export class AdvisorChatWidgetComponent {
  private readonly authSession = inject(AuthSessionService);
  private readonly advisorService = inject(AdvisorService);

  readonly isAuthenticated = this.authSession.isAuthenticated;
  readonly chatOpen = signal(false);
  readonly chatSending = signal(false);
  readonly chatErrorMessage = signal('');
  readonly conversation = signal<ConversationEntry[]>(this.restoreConversation());
  readonly draft = signal('');
  readonly maxLength = ADVISOR_MAX_MESSAGE_LENGTH;
  readonly canSend = computed(() => {
    const text = this.draft().trim();
    return !this.chatSending() && text.length > 0 && text.length <= ADVISOR_MAX_MESSAGE_LENGTH;
  });

  readonly suggestions = [
    'What does my portfolio look like right now?',
    'How much of my money is in US stocks, in rupees?',
    'Which of my holdings is up or down the most against what I paid?',
    'Explain the current buy, sell and hold signals.'
  ];

  toggleChat(): void {
    this.chatOpen.update((open) => !open);
  }

  closeChat(): void {
    this.chatOpen.set(false);
  }

  useSuggestion(question: string): void {
    this.chatOpen.set(true);
    this.draft.set(question);
    this.send();
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  send(): void {
    if (!this.canSend()) {
      return;
    }

    const question = this.draft().trim();
    const conversation = [...this.conversation(), this.entry('user', question)];
    this.conversation.set(conversation);
    this.draft.set('');
    this.chatErrorMessage.set('');
    this.chatSending.set(true);

    this.advisorService
      .chat(conversation.map(({ role, content }) => ({ role, content })))
      .pipe(finalize(() => this.chatSending.set(false)))
      .subscribe({
        next: (response) => {
          this.conversation.update((entries) => [
            ...entries,
            { ...this.entry('assistant', response.reply), toolsUsed: response.toolsUsed }
          ]);
          this.persistConversation();
          this.chatOpen.set(true);
        },
        error: (error: unknown) => {
          this.conversation.update((entries) => entries.slice(0, -1));
          this.draft.set(question);
          this.chatErrorMessage.set(this.toReadableChatError(error));
          this.persistConversation();
        }
      });
  }

  clearConversation(): void {
    this.conversation.set([]);
    this.chatErrorMessage.set('');
    this.persistConversation();
  }

  sourcesLabel(toolsUsed: string[] | undefined): string {
    if (!toolsUsed || toolsUsed.length === 0) {
      return '';
    }
    return 'Based on ' + toolsUsed.map((tool) => TOOL_LABELS[tool] ?? tool).join(', ');
  }

  private entry(role: 'user' | 'assistant', content: string): ConversationEntry {
    return { role, content, html: role === 'assistant' ? renderAdvisorMarkdown(content) : '' };
  }

  private toReadableChatError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const code = (error.error as { errorCode?: unknown } | null)?.errorCode;
      if (typeof code === 'string' && code in ADVISOR_ERROR_MESSAGES) {
        return ADVISOR_ERROR_MESSAGES[code];
      }
    }
    return toUserFacingErrorMessage(error, { fallbackMessage: 'The advisor could not answer. Please try again.' });
  }

  private storageKey(): string {
    return `${STORAGE_KEY}.${this.authSession.claims()?.accountId ?? 'anonymous'}`;
  }

  private persistConversation(): void {
    try {
      const plain = this.conversation().map(({ role, content, toolsUsed }) => ({ role, content, toolsUsed }));
      sessionStorage.setItem(this.storageKey(), JSON.stringify(plain));
    } catch {
    }
  }

  private restoreConversation(): ConversationEntry[] {
    try {
      const saved = JSON.parse(sessionStorage.getItem(this.storageKey()) ?? '[]') as ConversationEntry[];
      return Array.isArray(saved)
        ? saved
            .filter((m) => (m.role === 'user' || m.role === 'assistant') && typeof m.content === 'string')
            .map((m) => ({ ...this.entry(m.role, m.content), toolsUsed: m.toolsUsed }))
        : [];
    } catch {
      return [];
    }
  }
}