// Link Streamz push sender — Cloudflare Worker reference.
// Sends data-only FCM messages; the Android app builds the notification itself.
//
// Required secrets (wrangler secret put ...):
//   FIREBASE_PROJECT_ID
//   FIREBASE_CLIENT_EMAIL
//   FIREBASE_PRIVATE_KEY   (PEM, \n escapes are normalised below)
// Optional secret:
//   WORKER_TOKEN           (when set, callers must send Authorization: Bearer <token>)

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const TOKEN_URL = "https://oauth2.googleapis.com/token";

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));
    if (request.method !== "POST") return cors(json({ error: "POST only" }, 405));

    if (env.WORKER_TOKEN) {
      const auth = request.headers.get("Authorization") || "";
      if (auth !== `Bearer ${env.WORKER_TOKEN}`) {
        return cors(json({ error: "unauthorized" }, 401));
      }
    }

    let body;
    try {
      body = await request.json();
    } catch {
      return cors(json({ error: "invalid JSON" }, 400));
    }

    const title = String(body.title || "").trim();
    const message = String(body.body || body.message || "").trim();
    if (!title && !message) return cors(json({ error: "title or body required" }, 400));

    const format = ["std", "big", "pic"].includes(body.format) ? body.format : "std";
    const target = ["home", "movies", "events", "channels", "downloads"].includes(body.target)
      ? body.target
      : "home";
    const topics = Array.isArray(body.topics) && body.topics.length
      ? body.topics
      : [body.topic || "all"];

    const data = {
      title,
      body: message,
      format,
      target,
      sentAt: String(Date.now()),
    };
    if (body.image) data.image = String(body.image);
    if (body.button?.label && body.button?.url) {
      data.buttonLabel = String(body.button.label);
      data.buttonUrl = String(body.button.url);
    }

    try {
      const accessToken = await getAccessToken(env);
      const results = [];
      for (const topic of topics) {
        const res = await fetch(
          `https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`,
          {
            method: "POST",
            headers: {
              Authorization: `Bearer ${accessToken}`,
              "Content-Type": "application/json",
            },
            body: JSON.stringify({
              message: {
                topic: String(topic),
                data,
                android: { priority: "high" },
              },
            }),
          }
        );
        results.push({ topic, status: res.status, body: await res.text() });
      }
      return cors(json({ ok: true, results }));
    } catch (err) {
      return cors(json({ error: String(err?.message || err) }, 500));
    }
  },
};

function json(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function cors(response) {
  response.headers.set("Access-Control-Allow-Origin", "*");
  response.headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
  response.headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  return response;
}

async function getAccessToken(env) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: "RS256", typ: "JWT" };
  const claim = {
    iss: env.FIREBASE_CLIENT_EMAIL,
    scope: FCM_SCOPE,
    aud: TOKEN_URL,
    iat: now,
    exp: now + 3600,
  };
  const unsigned = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(claim))}`;
  const key = await importPrivateKey(env.FIREBASE_PRIVATE_KEY);
  const signature = await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    key,
    new TextEncoder().encode(unsigned)
  );
  const assertion = `${unsigned}.${b64urlBytes(new Uint8Array(signature))}`;

  const res = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const payload = await res.json();
  if (!res.ok || !payload.access_token) {
    throw new Error(`OAuth failed: ${res.status} ${JSON.stringify(payload)}`);
  }
  return payload.access_token;
}

async function importPrivateKey(pem) {
  const normalised = String(pem || "").replace(/\\n/g, "\n");
  const body = normalised
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  if (!body) throw new Error("FIREBASE_PRIVATE_KEY is missing");
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  return crypto.subtle.importKey(
    "pkcs8",
    der.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
}

function b64url(text) {
  return b64urlBytes(new TextEncoder().encode(text));
}

function b64urlBytes(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
