import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AdvisorService } from '../api/advisor.service';
import { AuthSessionService } from '../auth/auth-session.service';
import { AdvisorChatWidgetComponent } from './advisor-chat-widget.component';

const advisorServiceMock = {
  chat: vi.fn()
};

const authSessionMock = {
  isAuthenticated: () => true,
  claims: () => ({ accountId: 103 })
};

describe('AdvisorChatWidgetComponent', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    advisorServiceMock.chat.mockReset();

    await TestBed.configureTestingModule({
      imports: [AdvisorChatWidgetComponent],
      providers: [
        { provide: AdvisorService, useValue: advisorServiceMock },
        { provide: AuthSessionService, useValue: authSessionMock }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    sessionStorage.clear();
  });

  it('opens the chat widget and sends a question to the advisor', () => {
    advisorServiceMock.chat.mockReturnValue(
      of({
        reply: 'Your largest holding is **ITC.NS**.',
        toolsUsed: ['get_portfolio'],
        model: 'gemini-2.5-flash',
        generatedAt: '2026-10-04T10:00:00Z'
      })
    );

    const fixture = TestBed.createComponent(AdvisorChatWidgetComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('[data-testid="advisor-chat-fab"]') as HTMLButtonElement).click();
    component.draft.set('  What is my largest holding?  ');
    component.send();
    fixture.detectChanges();

    expect(advisorServiceMock.chat).toHaveBeenCalledWith([{ role: 'user', content: 'What is my largest holding?' }]);
    const reply = fixture.nativeElement.querySelector('[data-testid="advisor-reply"]') as HTMLElement;
    expect(reply.innerHTML).toContain('<strong>ITC.NS</strong>');
    expect(reply.textContent).toContain('Based on your portfolio');
  });

  it('shows a friendly chat error and restores the draft when the chatbot is rate limited', () => {
    advisorServiceMock.chat.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 429,
            error: { errorCode: 'ADV-429', message: 'The advisor is busy.' }
          })
      )
    );

    const fixture = TestBed.createComponent(AdvisorChatWidgetComponent);
    const component = fixture.componentInstance;
    fixture.detectChanges();

    component.draft.set('How is AAPL doing?');
    component.send();
    fixture.detectChanges();

    expect(component.conversation()).toHaveLength(0);
    expect(component.draft()).toBe('How is AAPL doing?');
    expect(component.chatErrorMessage()).toContain('busy');
  });
});