# Link Streamz push sender (Cloudflare Worker)

Reference sender for **data-only** FCM messages. The Android app builds and shows the
notification on-device, so the payload never relies on FCM's own notification rendering.

## 1. Firebase service account

1. Firebase console → Project settings → Service accounts → **Generate new private key**.
2. From the downloaded JSON, take `project_id`, `client_email`, `private_key`.

## 2. Deploy

```bash
cd worker
npm install -g wrangler            # if needed
wrangler login
wrangler secret put FIREBASE_CLIENT_EMAIL
wrangler secret put FIREBASE_PRIVATE_KEY     # paste the full PEM including BEGIN/END lines
wrangler secret put WORKER_TOKEN             # optional shared secret
wrangler deploy
```

`FIREBASE_PROJECT_ID` is set in `wrangler.toml`.

## 3. Send a message

```bash
curl -X POST "https://link-streamz-push.<you>.workers.dev" \
  -H "Authorization: Bearer $WORKER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "New movie is live",
    "body": "The limited-time movie is now available for 7 days.",
    "format": "pic",
    "image": "https://example.com/poster.jpg",
    "target": "movies",
    "button": { "label": "Open movies", "url": "https://example.com/movies" }
  }'
```

* `topics` (array) / `topic` (string) – defaults to `all`. The Android app subscribes every
  install to `all` on startup.
* `format` – `std`, `big` (expanded text) or `pic` (big picture).
* `target` – `home`, `movies`, `events`, `channels`, `downloads`. Tapping opens that section.
* `button` – optional; the app opens `buttonUrl` when tapped.

## Android checklist

* `google-services.json` is committed for package `com.linkstreamz.app`.
* `POST_NOTIFICATIONS` runtime permission is requested once on first launch.
* Channels: `link_streamz_announcements` (high, sound) and `link_streamz_downloads`
  (low, silent).
