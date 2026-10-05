import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import {
  ADVISOR_MAX_MESSAGE_LENGTH,
  AdvisorChatMessage,
  AdvisorService
} from '../api/advisor.service';
import { toUserFacingErrorMessage } from '../api/error-catalogue';
import { AuthSessionService } from '../auth/auth-session.service';
import { ThemeService } from '../theme/theme.service';
import { renderAdvisorMarkdown } from './advisor-markdown';

interface ConversationEntry extends AdvisorChatMessage {
  html: string;
  toolsUsed?: string[];
}

/** Friendly labels for the tool names in AdvisorChatResponse.toolsUsed. */
const TOOL_LABELS: Readonly<Record<string, string>> = {
  get_portfolio: 'your portfolio',
  get_quotes: 'stored prices',
  get_fx_rate: 'FX rates',
  search_instruments: 'instrument search',
  get_order_history: 'your orders'
};

const ADVISOR_ERROR_MESSAGES: Readonly<Record<string, string>> = {
  'ADV-429': 'The advisor is busy right now. Wait a minute and try again.',
  'ADV-503': 'The advisor is unavailable right now. Please try again later.'
};

const STORAGE_KEY = 'trade-ui.advisor.conversation';

@Component({
  selector: 'app-advisor',
  imports: [FormsModule, RouterLink],
  templateUrl: './advisor.component.html',
  styleUrl: './advisor.component.css'
})
export class AdvisorComponent {
  private readonly authSession = inject(AuthSessionService);
  private readonly advisorService = inject(AdvisorService);
  private readonly router = inject(Router);
  private readonly themeService = inject(ThemeService);
  private readonly threadEnd = viewChild<ElementRef<HTMLElement>>('threadEnd');

  readonly claims = this.authSession.claims;
  readonly role = computed(() => this.claims()?.roles[0] ?? 'USER');
  readonly theme = this.themeService.theme;
  readonly maxLength = ADVISOR_MAX_MESSAGE_LENGTH;

  readonly conversation = signal<ConversationEntry[]>(this.restore());
  readonly draft = signal('');
  readonly sending = signal(false);
  readonly errorMessage = signal('');
  readonly canSend = computed(() => {
    const text = this.draft().trim();
    return !this.sending() && text.length > 0 && text.length <= ADVISOR_MAX_MESSAGE_LENGTH;
  });

  readonly suggestions = [
    'What does my portfolio look like right now?',
    'How much of my money is in US stocks, in rupees?',
    'Which of my holdings is up or down the most against what I paid?',
    'Summarise my last 5 orders.'
  ];

  constructor() {
    this.themeService.initialize();
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }

  signOut(): void {
    this.authSession.signOut();
    void this.router.navigateByUrl('/sign-in');
  }

  useSuggestion(question: string): void {
    this.draft.set(question);
    this.send();
  }

  onKeydown(event: KeyboardEvent): void {
    // Enter sends; Shift+Enter starts a new line.
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
    this.errorMessage.set('');
    this.sending.set(true);
    this.scrollToEnd();

    this.advisorService
      .chat(conversation.map(({ role, content }) => ({ role, content })))
      .pipe(finalize(() => this.sending.set(false)))
      .subscribe({
        next: (response) => {
          this.conversation.update((entries) => [
            ...entries,
            { ...this.entry('assistant', response.reply), toolsUsed: response.toolsUsed }
          ]);
          this.persist();
          this.scrollToEnd();
        },
        error: (error: unknown) => {
          // Put the question back so the user can retry without retyping it.
          this.conversation.update((entries) => entries.slice(0, -1));
          this.draft.set(question);
          this.errorMessage.set(this.toReadableError(error));
        }
      });
  }

  clearConversation(): void {
    this.conversation.set([]);
    this.errorMessage.set('');
    this.persist();
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

  private toReadableError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const code = (error.error as { errorCode?: unknown } | null)?.errorCode;
      if (typeof code === 'string' && code in ADVISOR_ERROR_MESSAGES) {
        return ADVISOR_ERROR_MESSAGES[code];
      }
    }
    return toUserFacingErrorMessage(error, { fallbackMessage: 'The advisor could not answer. Please try again.' });
  }

  private scrollToEnd(): void {
    setTimeout(() => this.threadEnd()?.nativeElement.scrollIntoView?.({ behavior: 'smooth', block: 'end' }));
  }

  /** One saved conversation per account, so another user signing in on this tab never sees it. */
  private storageKey(): string {
    return `${STORAGE_KEY}.${this.claims()?.accountId ?? 'anonymous'}`;
  }

  /** Keeps the conversation for this browser tab, so moving between pages does not lose it. */
  private persist(): void {
    try {
      const plain = this.conversation().map(({ role, content, toolsUsed }) => ({ role, content, toolsUsed }));
      sessionStorage.setItem(this.storageKey(), JSON.stringify(plain));
    } catch {
      // Storage unavailable (private mode, quota); the conversation simply is not kept.
    }
  }

  private restore(): ConversationEntry[] {
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
