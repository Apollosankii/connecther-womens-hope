// Notify an app user via FCM using tokens stored in public.devices.
// Call from Admin Portal (e.g. after approving a provider) or from other Edge Functions.
//
// Required secrets (Supabase Dashboard → Edge Functions → notify-app-user → Secrets):
//   FIREBASE_PROJECT_ID     - Firebase project ID (e.g. connecther-47cce)
//   FIREBASE_CLIENT_EMAIL   - Service account client_email from Firebase Console
//   FIREBASE_PRIVATE_KEY    - Service account private_key (full PEM, with \n as literal)
//   SUPABASE_URL            - Set automatically by Supabase
//   SUPABASE_SERVICE_ROLE_KEY - Set automatically by Supabase
//
// Optional: NOTIFY_SECRET - If set, request must include header "x-notify-secret: <value>"

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-notify-secret",
};

interface NotifyBody {
  user_id: number | string; // public.users.id (internal PK); string allowed for JSON from pg_net
  title?: string;
  body?: string;
  data?: Record<string, string>;
}

function parseUserId(raw: unknown): number | null {
  if (typeof raw === "number" && Number.isFinite(raw)) {
    const n = Math.trunc(raw);
    return n >= 1 ? n : null;
  }
  if (typeof raw === "string" && raw.trim() !== "") {
    const n = parseInt(raw.trim(), 10);
    return Number.isFinite(n) && n >= 1 ? n : null;
  }
  return null;
}

async function getFirebaseAccessToken(): Promise<string> {
  const clientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL")?.trim();
  const privateKeyPem = Deno.env.get("FIREBASE_PRIVATE_KEY")?.trim();
  if (!clientEmail || !privateKeyPem) {
    throw new Error("FIREBASE_CLIENT_EMAIL and FIREBASE_PRIVATE_KEY must be set");
  }
  // Decode PEM: replace escaped newlines and parse
  const privateKey = privateKeyPem.replace(/\\n/g, "\n");

  const now = Math.floor(Date.now() / 1000);
  const payload = {
    iss: clientEmail,
    sub: clientEmail,
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
  };

  const header = { alg: "RS256", typ: "JWT" };
  const encodedHeader = btoa(JSON.stringify(header)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  const encodedPayload = btoa(JSON.stringify(payload)).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
  const signatureInput = `${encodedHeader}.${encodedPayload}`;

  const pemContents = privateKey
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const binaryKey = Uint8Array.from(atob(pemContents), (c) => c.charCodeAt(0));

  const key = await crypto.subtle.importKey(
    "pkcs8",
    binaryKey,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );

  const encoder = new TextEncoder();
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    encoder.encode(signatureInput)
  );
  const encodedSignature = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
  const jwt = `${signatureInput}.${encodedSignature}`;

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!tokenRes.ok) {
    const text = await tokenRes.text();
    throw new Error(`Failed to get access token: ${tokenRes.status} ${text}`);
  }
  const tokenData = await tokenRes.json();
  const accessToken = tokenData.access_token;
  if (!accessToken) throw new Error("No access_token in response");
  return accessToken;
}

async function sendFcm(accessToken: string, projectId: string, token: string, title: string, body: string, data: Record<string, string>): Promise<void> {
  const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;
  const message = {
    message: {
      token,
      notification: { title, body },
      data: Object.fromEntries(Object.entries(data).map(([k, v]) => [k, String(v)])),
      android: { priority: "high" as const },
    },
  };
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(message),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`FCM send failed: ${res.status} ${text}`);
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const notifySecret = Deno.env.get("NOTIFY_SECRET")?.trim();
  if (notifySecret) {
    const provided = req.headers.get("x-notify-secret");
    if (provided !== notifySecret) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), {
        status: 401,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }
  }

  let body: NotifyBody;
  try {
    body = await req.json();
  } catch {
    return new Response(JSON.stringify({ error: "Invalid JSON body" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const userId = parseUserId(body.user_id);
  if (userId == null) {
    return new Response(JSON.stringify({ error: "user_id (positive integer) is required" }), {
      status: 400,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const title = (body.title ?? "Notification").trim() || "ConnectHer";
  const bodyText = (body.body ?? "").trim() || "You have a new notification.";
  const data = body.data ?? {};

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!supabaseUrl || !serviceRoleKey) {
    return new Response(JSON.stringify({ error: "Supabase config missing" }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  const projectId = Deno.env.get("FIREBASE_PROJECT_ID")?.trim();
  if (!projectId) {
    return new Response(JSON.stringify({
      error: "FIREBASE_PROJECT_ID is not set. Add it in Edge Function secrets.",
    }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }

  try {
    const tokensRes = await fetch(`${supabaseUrl}/rest/v1/devices?user_id=eq.${userId}&select=reg_token`, {
      headers: {
        apikey: serviceRoleKey,
        Authorization: `Bearer ${serviceRoleKey}`,
        "Content-Type": "application/json",
      },
    });
    if (!tokensRes.ok) {
      const text = await tokensRes.text();
      throw new Error(`Devices fetch failed: ${tokensRes.status} ${text}`);
    }
    const tokensRows = await tokensRes.json();
    const tokens: string[] = Array.isArray(tokensRows)
      ? tokensRows.map((r: { reg_token?: string }) => r?.reg_token).filter(Boolean)
      : [];
    if (tokens.length === 0) {
      return new Response(JSON.stringify({ success: true, sent: 0, message: "No devices registered for user" }), {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      });
    }

    const accessToken = await getFirebaseAccessToken();
    let sent = 0;
    for (const token of tokens) {
      try {
        await sendFcm(accessToken, projectId, token, title, bodyText, data);
        sent++;
      } catch (e) {
        console.error("FCM send error for token:", e);
      }
    }

    return new Response(JSON.stringify({ success: true, sent, total: tokens.length }), {
      status: 200,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return new Response(JSON.stringify({ error: "Notify failed", detail: message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
