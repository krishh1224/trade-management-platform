import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, vi } from 'vitest';

import { TradeService } from '../api/trade.service';
import {
  BrowserPushService,
  NAVIGATE_MESSAGE,
  PUSH_SERVICE_WORKER_URL,
  urlBase64ToUint8Array
} from './browser-push.service';

const ENDPOINT = 'https://fcm.googleapis.com/fcm/send/abc';
const SERVER_KEY = 'BAECAwQ'; // base64url of bytes 4,1,2,3,4

describe('BrowserPushService', () => {
  const tradeServiceMock = {
    getPushConfig: vi.fn(),
    savePushSubscription: vi.fn(),
    deletePushSubscription: vi.fn()
  };

  let subscription: {
    endpoint: string;
    options: { applicationServerKey: ArrayBuffer | null };
    toJSON: () => unknown;
    unsubscribe: ReturnType<typeof vi.fn>;
  };
  let pushManager: { getSubscription: ReturnType<typeof vi.fn>; subscribe: ReturnType<typeof vi.fn> };
  let serviceWorker: Record<string, unknown>;
  let requestPermission: ReturnType<typeof vi.fn>;

  function installBrowserApis(permission: NotificationPermission = 'default'): void {
    subscription = {
      endpoint: ENDPOINT,
      options: { applicationServerKey: urlBase64ToUint8Array(SERVER_KEY).buffer },
      toJSON: () => ({ endpoint: ENDPOINT, keys: { p256dh: 'p', auth: 'a' } }),
      unsubscribe: vi.fn().mockResolvedValue(true)
    };
    pushManager = {
      getSubscription: vi.fn().mockResolvedValue(null),
      subscribe: vi.fn().mockResolvedValue(subscription)
    };
    const registration = { pushManager, update: vi.fn().mockResolvedValue(undefined) };
    serviceWorker = {
      register: vi.fn().mockResolvedValue(registration),
      ready: Promise.resolve(registration),
      getRegistration: vi.fn().mockResolvedValue(registration),
      addEventListener: vi.fn(),
      startMessages: vi.fn()
    };
    requestPermission = vi.fn().mockResolvedValue('granted');
    Object.defineProperty(navigator, 'serviceWorker', { value: serviceWorker, configurable: true });
    Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true });
    vi.stubGlobal('PushManager', class {});
    vi.stubGlobal('Notification', { permission, requestPermission });
  }

  function service(): BrowserPushService {
    TestBed.configureTestingModule({ providers: [{ provide: TradeService, useValue: tradeServiceMock }] });
    return TestBed.inject(BrowserPushService);
  }

  beforeEach(() => {
    vi.clearAllMocks();
    tradeServiceMock.getPushConfig.mockReturnValue(of({ enabled: true, publicKey: SERVER_KEY }));
    tradeServiceMock.savePushSubscription.mockReturnValue(of(undefined));
    tradeServiceMock.deletePushSubscription.mockReturnValue(of(undefined));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    Reflect.deleteProperty(navigator, 'serviceWorker');
  });

  it('decodes the base64url VAPID key into raw bytes', () => {
    expect(Array.from(urlBase64ToUint8Array(SERVER_KEY))).toEqual([4, 1, 2, 3, 4]);
  });

  it('reports unsupported when the browser has no Push API', async () => {
    const push = service();

    await push.refresh();

    expect(push.state()).toBe('unsupported');
  });

  it('enables push: asks permission, subscribes with the server key and registers the browser', async () => {
    installBrowserApis();
    const push = service();

    await push.enable(101);

    expect(requestPermission).toHaveBeenCalled();
    expect(serviceWorker['register']).toHaveBeenCalledWith(PUSH_SERVICE_WORKER_URL);
    expect(pushManager.subscribe).toHaveBeenCalledWith({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(SERVER_KEY)
    });
    expect(tradeServiceMock.savePushSubscription).toHaveBeenCalledWith(101, {
      endpoint: ENDPOINT,
      keys: { p256dh: 'p', auth: 'a' }
    });
    expect(push.state()).toBe('on');
  });

  it('does not prompt when push is switched off on the server', async () => {
    installBrowserApis();
    tradeServiceMock.getPushConfig.mockReturnValue(of({ enabled: false, publicKey: null }));
    const push = service();

    await push.enable(101);

    expect(requestPermission).not.toHaveBeenCalled();
    expect(push.state()).toBe('unavailable');
  });

  it('stops when the customer refuses notifications', async () => {
    installBrowserApis();
    requestPermission.mockResolvedValue('denied');
    const push = service();

    await push.enable(101);

    expect(pushManager.subscribe).not.toHaveBeenCalled();
    expect(tradeServiceMock.savePushSubscription).not.toHaveBeenCalled();
    expect(push.state()).toBe('blocked');
  });

  it('reports blocked without prompting when notifications were already refused', async () => {
    installBrowserApis('denied');
    const push = service();

    await push.refresh();

    expect(push.state()).toBe('blocked');
  });

  it('disables push: removes the browser from the server and unsubscribes', async () => {
    installBrowserApis();
    pushManager.getSubscription.mockResolvedValue(subscription);
    const push = service();

    await push.disable(101);

    expect(tradeServiceMock.deletePushSubscription).toHaveBeenCalledWith(101, ENDPOINT);
    expect(subscription.unsubscribe).toHaveBeenCalled();
    expect(push.state()).toBe('off');
  });

  it('opens the inbox when the service worker reports a clicked pop-up', () => {
    installBrowserApis();
    const navigate = vi.fn();
    service().listenForPopUpClicks(navigate);

    const onMessage = (serviceWorker['addEventListener'] as ReturnType<typeof vi.fn>).mock.calls[0][1];
    onMessage({ data: { type: NAVIGATE_MESSAGE, url: '/notifications' } });

    expect(serviceWorker['startMessages']).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith('/notifications');
  });

  it('ignores messages that are not a same-origin path', () => {
    installBrowserApis();
    const navigate = vi.fn();
    service().listenForPopUpClicks(navigate);

    const onMessage = (serviceWorker['addEventListener'] as ReturnType<typeof vi.fn>).mock.calls[0][1];
    onMessage({ data: { type: NAVIGATE_MESSAGE, url: 'https://evil.example/' } });
    onMessage({ data: { type: NAVIGATE_MESSAGE, url: '//evil.example/' } });
    onMessage({ data: { type: 'something-else', url: '/notifications' } });

    expect(navigate).not.toHaveBeenCalled();
  });
});
