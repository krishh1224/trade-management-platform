import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Base URL of advisor-api (contracts/advisor-api.yaml). Provided by provideApi(). */
export const ADVISOR_API_URL = new InjectionToken<string>('ADVISOR_API_URL', {
  factory: () => 'http://localhost:8083'
});

export type AdvisorChatRole = 'user' | 'assistant';

export interface AdvisorChatMessage {
  role: AdvisorChatRole;
  content: string;
}

export interface AdvisorChatResponse {
  reply: string;
  toolsUsed: string[];
  model: string;
  generatedAt: string;
}

/** Contract limits: at most 20 messages per request, each at most 4,000 characters. */
export const ADVISOR_MAX_MESSAGES = 20;
export const ADVISOR_MAX_MESSAGE_LENGTH = 4000;

/** Hand-written client for advisor-api's single endpoint. */
@Injectable({ providedIn: 'root' })
export class AdvisorService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(ADVISOR_API_URL);

  /** Sends the conversation (oldest first, last message from the user) and returns the next reply. */
  chat(messages: AdvisorChatMessage[]): Observable<AdvisorChatResponse> {
    return this.http.post<AdvisorChatResponse>(`${this.baseUrl}/api/v1/advisor/chat`, {
      messages: messages.slice(-ADVISOR_MAX_MESSAGES)
    });
  }
}
