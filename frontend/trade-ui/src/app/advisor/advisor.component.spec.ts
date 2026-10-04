import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AdvisorService } from '../api/advisor.service';
import { AdvisorComponent } from './advisor.component';

const advisorServiceMock = {
  chat: vi.fn()
};

describe('AdvisorComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    sessionStorage.clear();
    localStorage.setItem(
      'trade-ui.auth.session',
      JSON.stringify({ accessToken: buildToken(), expiresIn: 900, refreshToken: 'refresh', tokenType: 'Bearer' })
    );
    advisorServiceMock.chat.mockReset();

    await TestBed.configureTestingModule({
      imports: [AdvisorComponent],
      providers: [provideRouter([]), { provide: AdvisorService, useValue: advisorServiceMock }]
    }).compileComponents();
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it('shows suggestions and the disclaimer before the first question', () => {
    const fixture = TestBed.createComponent(AdvisorComponent);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('not investment advice');
    expect(fixture.nativeElement.querySelectorAll('.suggestion').length).toBe(4);
  });

  it('sends the whole conversation and renders the reply with its sources', () => {
    advisorServiceMock.chat.mockReturnValue(
      of({
        reply: 'Your largest holding is **ITC.NS**.',
        toolsUsed: ['get_portfolio'],
        model: 'gemini-2.5-flash',
        generatedAt: '2026-10-04T10:00:00Z'
      })
    );
    const fixture = TestBed.createComponent(AdvisorComponent);
    const component = fixture.componentInstance;

    component.draft.set('  What is my largest holding?  ');
    component.send();
    fixture.detectChanges();

    expect(advisorServiceMock.chat).toHaveBeenCalledWith([{ role: 'user', content: 'What is my largest holding?' }]);
    const reply = fixture.nativeElement.querySelector('[data-testid="advisor-reply"]') as HTMLElement;
    expect(reply.innerHTML).toContain('<strong>ITC.NS</strong>');
    expect(reply.textContent).toContain('Based on your portfolio');
    expect(component.draft()).toBe('');
    expect(component.conversation()).toHaveLength(2);
  });

  it('on a rate limit, restores the question and shows a friendly message', () => {
    advisorServiceMock.chat.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 429,
            error: { errorCode: 'ADV-429', message: 'The advisor is busy.' }
          })
      )
    );
    const fixture = TestBed.createComponent(AdvisorComponent);
    const component = fixture.componentInstance;

    component.draft.set('How is AAPL doing?');
    component.send();
    fixture.detectChanges();

    expect(component.conversation()).toHaveLength(0);
    expect(component.draft()).toBe('How is AAPL doing?');
    expect(component.errorMessage()).toContain('busy');
  });

  it('does not send an empty question', () => {
    const fixture = TestBed.createComponent(AdvisorComponent);
    fixture.componentInstance.draft.set('   ');
    fixture.componentInstance.send();

    expect(advisorServiceMock.chat).not.toHaveBeenCalled();
  });

  it('keeps the conversation per account across page visits and clears it on New chat', () => {
    advisorServiceMock.chat.mockReturnValue(
      of({ reply: 'Hello', toolsUsed: [], model: 'gemini-2.5-flash', generatedAt: '2026-10-04T10:00:00Z' })
    );
    const first = TestBed.createComponent(AdvisorComponent).componentInstance;
    first.draft.set('Hi');
    first.send();

    const second = TestBed.createComponent(AdvisorComponent).componentInstance;
    expect(second.conversation().map((m) => m.content)).toEqual(['Hi', 'Hello']);

    second.clearConversation();
    expect(TestBed.createComponent(AdvisorComponent).componentInstance.conversation()).toHaveLength(0);
  });
});

function buildToken(): string {
  const header = toBase64Url(JSON.stringify({ alg: 'none', typ: 'JWT' }));
  const payload = toBase64Url(
    JSON.stringify({ sub: 'team5', accountId: 103, roles: ['USER'], iat: 1_700_000_000, exp: 4_102_444_800 })
  );

  return `${header}.${payload}.sig`;
}

function toBase64Url(value: string): string {
  return btoa(value).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
