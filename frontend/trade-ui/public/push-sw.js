// Artha push service worker: shows the order pop-up the Trade REST API sends through the browser's
// push service, and opens the notifications inbox when the pop-up is clicked.
// Registered by BrowserPushService (src/app/notifications/browser-push.service.ts).

const NAVIGATE_MESSAGE = 'artha:navigate';

// take over immediately, including Artha tabs opened before this worker existed; without this an
// already-open tab is not "controlled" and the browser refuses to navigate it from here
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = { body: event.data ? event.data.text() : '' };
  }

  event.waitUntil(
    self.registration.showNotification(data.title || 'Artha', {
      body: data.body || 'You have a new order update.',
      tag: data.tag, // one pop-up per notification, even if it is delivered twice
      icon: '/favicon.ico',
      data: { url: data.url || '/notifications' }
    })
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const path = (event.notification.data && event.notification.data.url) || '/notifications';
  const target = new URL(path, self.location.origin);

  event.waitUntil(openInbox(target));
});

async function openInbox(target) {
  const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  const artha = windows.find((client) => new URL(client.url).origin === self.location.origin);

  if (!artha) {
    // no Artha tab open (or the browser was minimised with none): open the inbox in a new one
    return self.clients.openWindow(target.href);
  }

  try {
    await artha.focus();
  } catch {
    // focusing can be refused; navigating below still works
  }

  try {
    // works for tabs this worker controls
    await artha.navigate(target.href);
    return;
  } catch {
    // not controlled yet: let the Angular app route there itself
  }

  artha.postMessage({ type: NAVIGATE_MESSAGE, url: target.pathname + target.search });
}
