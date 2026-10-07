import { Provider, signal } from '@angular/core';

import { NotificationCountService } from './notification-count.service';

/** For page specs: the header bell renders without calling the notifications API. */
export function provideNotificationCountStub(unread = 0): Provider {
  return {
    provide: NotificationCountService,
    useValue: {
      unreadCount: signal(unread),
      refresh: () => undefined,
      set: () => undefined
    }
  };
}
