import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { TradeService } from '../api/trade.service';

/**
 * unsupported: this browser has no Push API (or the page is not on https/localhost)
 * unavailable: push is switched off on the server
 * blocked:     the customer refused notifications for this site in the browser
 * off / on:    whether this browser is subscribed
 */
export type PushState = 'unsupported' | 'unavailable' | 'blocked' | 'off' | 'on';

export const PUSH_SERVICE_WORKER_URL = '/push-sw.js';

/** Sent by push-sw.js when a pop-up is clicked and the tab cannot be navigated from the worker. */
export const NAVIGATE_MESSAGE = 'artha:navigate';

/** Turns the PUSH alert channel on or off for this browser. */
@Injectable({ providedIn: 'root' })
export class BrowserPushService {
  private readonly tradeService = inject(TradeService);

  readonly state = signal<PushState>('off');
  readonly busy = signal(false);
  readonly error = signal('');

  isSupported(): boolean {
    return (
      typeof window !== 'undefined' &&
      window.isSecureContext &&
      'serviceWorker' in navigator &&
      'PushManager' in window &&
      typeof Notification !== 'undefined'
    );
  }

  /** Reads the current state without prompting the customer. */
  async refresh(): Promise<void> {
    if (!this.isSupported()) {
      this.state.set('unsupported');
      return;
    }
    if (Notification.permission === 'denied') {
      this.state.set('blocked');
      return;
    }
    try {
      const registration = await navigator.serviceWorker.getRegistration(PUSH_SERVICE_WORKER_URL);
      // pick up a newer push-sw.js if one was deployed
      void registration?.update().catch(() => undefined);
      const subscription = await registration?.pushManager.getSubscription();
      this.state.set(subscription ? 'on' : 'off');
    } catch {
      this.state.set('off');
    }
  }

  /**
   * Routes the app when the service worker asks it to (a clicked pop-up whose tab the worker could
   * not navigate). Only same-origin paths are accepted, so a message cannot send the app elsewhere.
   */
  listenForPopUpClicks(navigate: (path: string) => void): void {
    if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) {
      return;
    }
    navigator.serviceWorker.addEventListener('message', (event: MessageEvent) => {
      const data = event.data as { type?: string; url?: string } | null;
      if (data?.type === NAVIGATE_MESSAGE && typeof data.url === 'string' && data.url.startsWith('/') && !data.url.startsWith('//')) {
        window.focus();
        navigate(data.url);
      }
    });
    navigator.serviceWorker.startMessages();
    // every app load checks for a newer push-sw.js, so fixes to the worker reach existing browsers
    void navigator.serviceWorker
      .getRegistration(PUSH_SERVICE_WORKER_URL)
      .then((registration) => registration?.update())
      .catch(() => undefined);
  }

  /** Asks for permission, subscribes this browser and registers it with the Trade REST API. */
  async enable(accountId: number): Promise<void> {
    this.error.set('');
    if (!this.isSupported()) {
      this.state.set('unsupported');
      return;
    }
    this.busy.set(true);
    try {
      const config = await firstValueFrom(this.tradeService.getPushConfig(accountId));
      if (!config.enabled || !config.publicKey) {
        this.state.set('unavailable');
        return;
      }

      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        this.state.set(permission === 'denied' ? 'blocked' : 'off');
        return;
      }

      await navigator.serviceWorker.register(PUSH_SERVICE_WORKER_URL);
      const registration = await navigator.serviceWorker.ready;
      const serverKey = urlBase64ToUint8Array(config.publicKey);

      let subscription = await registration.pushManager.getSubscription();
      if (subscription && !sameKey(subscription.options.applicationServerKey, serverKey)) {
        // subscribed against an older server key: start again with the current one
        await subscription.unsubscribe();
        subscription = null;
      }
      subscription ??= await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: serverKey
      });

      await firstValueFrom(this.tradeService.savePushSubscription(accountId, subscription.toJSON()));
      this.state.set('on');
    } catch {
      this.error.set('Could not turn on push notifications. Please try again.');
      await this.refresh();
    } finally {
      this.busy.set(false);
    }
  }

  /** Unsubscribes this browser and removes it from the Trade REST API. */
  async disable(accountId: number): Promise<void> {
    this.error.set('');
    if (!this.isSupported()) {
      return;
    }
    this.busy.set(true);
    try {
      const registration = await navigator.serviceWorker.getRegistration(PUSH_SERVICE_WORKER_URL);
      const subscription = await registration?.pushManager.getSubscription();
      if (subscription) {
        await firstValueFrom(this.tradeService.deletePushSubscription(accountId, subscription.endpoint));
        await subscription.unsubscribe();
      }
      this.state.set('off');
    } catch {
      this.error.set('Could not turn off push notifications. Please try again.');
    } finally {
      this.busy.set(false);
    }
  }
}

/** The VAPID public key arrives base64url-encoded; the Push API wants the raw bytes. */
export function urlBase64ToUint8Array(value: string): Uint8Array<ArrayBuffer> {
  const padded = (value + '='.repeat((4 - (value.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(padded);
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) {
    bytes[i] = raw.charCodeAt(i);
  }
  return bytes;
}

function sameKey(current: ArrayBuffer | null, expected: Uint8Array): boolean {
  if (!current || current.byteLength !== expected.length) {
    return false;
  }
  const bytes = new Uint8Array(current);
  return bytes.every((byte, i) => byte === expected[i]);
}
