/*
 * Service worker for Ironwood Document Services PWA.
 *
 * This worker caches the application shell on install and serves assets
 * from cache when offline. Cached resources are versioned via the cacheName.
 */

const cacheName = 'ironwood-pwa-v1';
const filesToCache = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './manifest.json',
  './assets/notary.png',
  './assets/i9.png',
  './assets/marriage.png',
  './assets/courier.png',
  './assets/icon_192.png',
  './assets/icon_512.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(cacheName).then((cache) => {
      return cache.addAll(filesToCache);
    })
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keyList) => {
      return Promise.all(
        keyList.map((key) => {
          if (key !== cacheName) {
            return caches.delete(key);
          }
        })
      );
    })
  );
  return self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  event.respondWith(
    caches.match(event.request).then((response) => {
      return response || fetch(event.request);
    })
  );
});