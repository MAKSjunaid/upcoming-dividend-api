/*
 * Service worker: makes the site installable as an app
 * (Android / desktop) and lets the app open offline.
 *
 * Rules:
 *  - /api/* is NEVER cached or touched (live data, SSE).
 *  - Page, scripts, styles, icons: network first, so users
 *    always get the latest version; the saved copy is used
 *    only when offline.
 */
const CACHE = "dividend-finder-v1";

self.addEventListener("install", function(event) {
    self.skipWaiting();
});

self.addEventListener("activate", function(event) {
    event.waitUntil(
        caches.keys().then(function(keys) {
            return Promise.all(
                keys
                    .filter(function(key) { return key !== CACHE; })
                    .map(function(key) { return caches.delete(key); })
            );
        }).then(function() {
            return self.clients.claim();
        })
    );
});

self.addEventListener("fetch", function(event) {

    const request = event.request;

    if (request.method !== "GET") {
        return;
    }

    const url = new URL(request.url);

    if (
        url.origin !== self.location.origin ||
        url.pathname.startsWith("/api/")
    ) {
        return;
    }

    event.respondWith(
        fetch(request)
            .then(function(response) {
                if (response && response.ok) {
                    const copy = response.clone();
                    caches.open(CACHE).then(function(cache) {
                        cache.put(request, copy);
                    });
                }
                return response;
            })
            .catch(function() {
                return caches.match(request).then(function(cached) {
                    return cached || caches.match("/");
                });
            })
    );
});
