/* ConnectHer Admin PWA Service Worker - v2: no cache for login/navigation */
const CACHE_NAME = 'connecther-admin-v2';

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k))
      )
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  /* Never cache navigation or login - always fetch from network */
  if (event.request.mode === 'navigate' || event.request.url.includes('/login') || event.request.url.includes('/api/') || event.request.method !== 'GET') {
    event.respondWith(
      fetch(event.request, { cache: 'no-store' }).catch(() =>
        caches.match(event.request).then((r) => r || caches.match('/'))
      )
    );
    return;
  }
  event.respondWith(
    fetch(event.request).then((response) => {
      const clone = response.clone();
      if (response.status === 200 && /\.(css|js|ico|png|jpg|jpeg|gif|svg|woff2?)(\?|$)/i.test(event.request.url)) {
        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
      }
      return response;
    }).catch(() => caches.match(event.request))
  );
});
