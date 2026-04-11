import { createClient } from "npm:@supabase/supabase-js@2.49.1";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@5.9.6";

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-clerk-authorization, x-client-info, apikey, content-type, x-firebase-id-token",
};

const MAX_RECIPIENTS = 5;
const MAX_SMS_BODY_CHARS = 1400;

/** Defaults when platform_settings row is missing or columns are null. */
const DEFAULT_MAX_DISPATCHES_24H = 6;
const DEFAULT_MIN_SECONDS_BETWEEN = 180;
const DEFAULT_MAX_GLOBAL_PER_HOUR = 200;

function clientIp(req: Request): string | null {
  const xff = req.headers.get("x-forwarded-for") ?? req.headers.get("X-Forwarded-For");
  if (xff) {
    const first = xff.split(",")[0]?.trim();
    if (first) return first.slice(0, 128);
  }
  const real = req.headers.get("x-real-ip") ?? req.headers.get("X-Real-Ip");
  if (real?.trim()) return real.trim().slice(0, 128);
  return null;
}

function corsJson(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function bearerToken(req: Request): string | null {
  const a = req.headers.get("Authorization") ?? req.headers.get("authorization");
  const m = a?.match(/^Bearer\s+(.+)$/i);
  return m ? m[1].trim() : null;
}

function firebaseIdTokenFromRequest(req: Request): string | null {
  const x = req.headers.get("X-Firebase-Id-Token") ?? req.headers.get("x-firebase-id-token");
  if (x) return x.replace(/^Bearer\s+/i, "").trim();
  return bearerToken(req);
}

function jwtPayloadUnsafe(token: string): Record<string, unknown> | null {
  const parts = token.split(".");
  if (parts.length < 2) return null;
  try {
    let b64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const pad = b64.length % 4;
    if (pad) b64 += "=".repeat(4 - pad);
    const json = atob(b64);
    return JSON.parse(json) as Record<string, unknown>;
  } catch {
    return null;
  }
}

async function verifyFirebaseIdToken(idToken: string) {
  const projectId = Deno.env.get("FIREBASE_PROJECT_ID")?.trim();
  if (!projectId) throw new Error("FIREBASE_PROJECT_ID missing");

  const jwks = createRemoteJWKSet(
    new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
  );

  const { payload } = await jwtVerify(idToken, jwks, {
    issuer: `https://securetoken.google.com/${projectId}`,
    audience: projectId,
  });

  const uid = typeof payload.sub === "string" ? payload.sub : "";
  if (!uid) throw new Error("Firebase token missing sub");

  return { uid };
}

function isValidE164(phone: string): boolean {
  const p = phone.trim();
  return /^\+[1-9]\d{6,14}$/.test(p);
}

function utcDateString(): string {
  return new Date().toISOString().slice(0, 10);
}

async function twilioSendSms(
  accountSid: string,
  authToken: string,
  to: string,
  body: string,
  messagingServiceSid: string | null,
  fromNumber: string | null,
): Promise<{ ok: boolean; error?: string; status?: number }> {
  const url =
    `https://api.twilio.com/2010-04-01/Accounts/${encodeURIComponent(accountSid)}/Messages.json`;
  const params = new URLSearchParams();
  params.set("To", to);
  params.set("Body", body);
  if (messagingServiceSid) {
    params.set("MessagingServiceSid", messagingServiceSid);
  } else if (fromNumber) {
    params.set("From", fromNumber);
  } else {
    return { ok: false, error: "Missing TWILIO_MESSAGING_SERVICE_SID or TWILIO_PANIC_FROM_NUMBER" };
  }
  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: `Basic ${btoa(`${accountSid}:${authToken}`)}`,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: params.toString(),
  });
  const raw = await res.text();
  if (!res.ok) {
    let msg = raw || res.statusText;
    try {
      const j = JSON.parse(raw) as { message?: string };
      if (j.message) msg = j.message;
    } catch {
      /* ignore */
    }
    return { ok: false, error: msg, status: res.status };
  }
  return { ok: true };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return corsJson({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  const accountSid = Deno.env.get("TWILIO_ACCOUNT_SID")?.trim();
  const authToken = Deno.env.get("TWILIO_AUTH_TOKEN")?.trim();
  const messagingServiceSid = Deno.env.get("TWILIO_MESSAGING_SERVICE_SID")?.trim() || null;
  const fromNumber = Deno.env.get("TWILIO_PANIC_FROM_NUMBER")?.trim() || null;

  if (!supabaseUrl || !serviceKey) {
    return corsJson({ code: "SERVER_MISCONFIG", error: "Supabase URL or service role missing" }, 500);
  }
  if (!accountSid || !authToken) {
    console.error("panic-sms: TWILIO_ACCOUNT_SID or TWILIO_AUTH_TOKEN missing");
    return corsJson(
      {
        code: "SERVER_MISCONFIG",
        error: "Twilio not configured",
        detail: "Set TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN.",
      },
      500,
    );
  }
  if (!messagingServiceSid && !fromNumber) {
    console.error("panic-sms: set TWILIO_MESSAGING_SERVICE_SID (MG…) or TWILIO_PANIC_FROM_NUMBER (E.164)");
    return corsJson(
      {
        code: "SERVER_MISCONFIG",
        error: "Twilio Messaging not configured",
        detail: "Set TWILIO_MESSAGING_SERVICE_SID or TWILIO_PANIC_FROM_NUMBER for outbound SMS.",
      },
      500,
    );
  }

  const idToken = firebaseIdTokenFromRequest(req);
  if (!idToken) {
    return corsJson(
      {
        code: "AUTH_REQUIRED",
        error: "Missing Firebase ID token",
        detail: "Send X-Firebase-Id-Token and Authorization: Bearer <Supabase anon key>.",
      },
      401,
    );
  }

  const payloadGuess = jwtPayloadUnsafe(idToken);
  const iss = String(payloadGuess?.iss ?? "");
  if (iss && !iss.includes("securetoken.google.com")) {
    return corsJson(
      {
        code: "WRONG_AUTH_HEADER",
        error: "Authorization carries a non-Firebase JWT in X-Firebase-Id-Token slot.",
        detail: `Token issuer: ${iss}. Put the Firebase ID token in X-Firebase-Id-Token and Supabase anon in Authorization.`,
      },
      400,
    );
  }

  let uid: string;
  try {
    const v = await verifyFirebaseIdToken(idToken);
    uid = v.uid;
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return corsJson(
      {
        code: "INVALID_TOKEN",
        error: "Invalid Firebase ID token",
        detail: msg,
      },
      401,
    );
  }

  let body: { recipients?: unknown; latitude?: unknown; longitude?: unknown };
  try {
    body = (await req.json()) as typeof body;
  } catch {
    return corsJson({ error: "Invalid JSON body" }, 400);
  }

  const rawRecipients = Array.isArray(body.recipients) ? body.recipients : [];
  const seen = new Set<string>();
  const recipients: string[] = [];
  for (const r of rawRecipients) {
    if (typeof r !== "string") continue;
    const t = r.trim();
    if (!t || seen.has(t)) continue;
    if (!isValidE164(t)) {
      return corsJson({ code: "INVALID_PHONE", error: "All recipients must be E.164 (e.g. +254712345678)" }, 400);
    }
    seen.add(t);
    recipients.push(t);
    if (recipients.length > MAX_RECIPIENTS) {
      return corsJson({ code: "TOO_MANY_RECIPIENTS", error: `At most ${MAX_RECIPIENTS} recipients` }, 400);
    }
  }
  if (recipients.length === 0) {
    return corsJson({ code: "NO_RECIPIENTS", error: "Provide recipients: string[] of E.164 numbers" }, 400);
  }

  const lat = typeof body.latitude === "number" && Number.isFinite(body.latitude) ? body.latitude : null;
  const lng = typeof body.longitude === "number" && Number.isFinite(body.longitude) ? body.longitude : null;

  const admin = createClient(supabaseUrl, serviceKey);

  const since24h = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  const since1h = new Date(Date.now() - 60 * 60 * 1000).toISOString();

  const [
    { data: settingsRow, error: settingsErr },
    { count: globalHourCount, error: globalErr },
    { count: userDayCount, error: userDayErr },
    { data: lastDispatchRows, error: lastErr },
  ] = await Promise.all([
    admin
      .from("platform_settings")
      .select(
        "panic_sms_max_dispatches_per_24h, panic_sms_min_seconds_between, panic_sms_max_global_per_hour",
      )
      .eq("id", 1)
      .maybeSingle(),
    admin
      .from("panic_sms_dispatch")
      .select("id", { count: "exact", head: true })
      .gte("created_at", since1h),
    admin
      .from("panic_sms_dispatch")
      .select("id", { count: "exact", head: true })
      .eq("firebase_uid", uid)
      .gte("created_at", since24h),
    admin
      .from("panic_sms_dispatch")
      .select("created_at")
      .eq("firebase_uid", uid)
      .order("created_at", { ascending: false })
      .limit(1),
  ]);

  if (settingsErr) {
    console.warn("panic-sms platform_settings read", settingsErr);
  }
  if (globalErr) console.warn("panic-sms global rate count", globalErr);
  if (userDayErr) console.warn("panic-sms user rate count", userDayErr);
  if (lastErr) console.warn("panic-sms last dispatch", lastErr);

  const s = settingsRow as Record<string, unknown> | null;
  const maxPerUser24h = Math.max(
    1,
    Math.min(
      200,
      Number(s?.panic_sms_max_dispatches_per_24h ?? DEFAULT_MAX_DISPATCHES_24H) ||
        DEFAULT_MAX_DISPATCHES_24H,
    ),
  );
  const minSecondsBetween = Math.max(
    0,
    Math.min(
      86400,
      Number(s?.panic_sms_min_seconds_between ?? DEFAULT_MIN_SECONDS_BETWEEN) ||
        DEFAULT_MIN_SECONDS_BETWEEN,
    ),
  );
  const maxGlobalPerHour = Math.max(
    10,
    Math.min(
      100_000,
      Number(s?.panic_sms_max_global_per_hour ?? DEFAULT_MAX_GLOBAL_PER_HOUR) ||
        DEFAULT_MAX_GLOBAL_PER_HOUR,
    ),
  );

  if ((globalHourCount ?? 0) >= maxGlobalPerHour) {
    return corsJson(
      {
        code: "RATE_LIMIT_GLOBAL",
        error: "Service temporarily busy. Please try again later.",
      },
      429,
    );
  }

  const lastCreated = lastDispatchRows?.[0]?.created_at as string | undefined;
  if (lastCreated && minSecondsBetween > 0) {
    const lastMs = new Date(lastCreated).getTime();
    if (Number.isFinite(lastMs)) {
      const elapsedSec = (Date.now() - lastMs) / 1000;
      if (elapsedSec < minSecondsBetween) {
        const wait = Math.ceil(minSecondsBetween - elapsedSec);
        return corsJson(
          {
            code: "RATE_LIMIT_COOLDOWN",
            error: "Please wait before sending another SMS alert.",
            detail: `Try again in about ${wait}s.`,
          },
          429,
        );
      }
    }
  }

  if ((userDayCount ?? 0) >= maxPerUser24h) {
    return corsJson(
      {
        code: "RATE_LIMIT",
        error: "Too many panic SMS dispatches in the last 24 hours for this account.",
      },
      429,
    );
  }

  const { data: userRow, error: uErr } = await admin
    .from("users")
    .select("id, first_name, last_name")
    .eq("clerk_user_id", uid)
    .maybeSingle();

  if (uErr) {
    console.error("panic-sms user lookup", uErr);
    return corsJson({ error: "Database lookup failed", detail: uErr.message }, 500);
  }
  if (userRow?.id == null) {
    return corsJson({ code: "NOT_SUBSCRIBED", error: "No account for this user" }, 403);
  }

  const today = utcDateString();
  const { data: subRows, error: sErr } = await admin
    .from("user_plan_subscriptions")
    .select("id")
    .eq("user_id", userRow.id)
    .eq("status", "active")
    .lte("started_at", today)
    .gte("expires_at", today)
    .order("expires_at", { ascending: false })
    .limit(1);

  if (sErr) {
    console.error("panic-sms subscription lookup", sErr);
    return corsJson({ error: "Subscription lookup failed", detail: sErr.message }, 500);
  }
  if (!subRows?.length) {
    return corsJson({ code: "NOT_SUBSCRIBED", error: "Active subscription required for ConnectHer SMS" }, 403);
  }

  const fn = (userRow.first_name as string | null)?.trim() ?? "";
  const ln = (userRow.last_name as string | null)?.trim() ?? "";
  const displayName = [fn, ln].filter(Boolean).join(" ").trim() || "A ConnectHer user";

  const locationText =
    lat != null && lng != null
      ? `Location: https://maps.google.com/?q=${lat},${lng}`
      : "Location: unavailable";

  let smsBody =
    `EMERGENCY ALERT: ${displayName} needs help! ` +
    `This is a GBV emergency alert from the ConnectHer app. ` +
    `${locationText}. Please respond immediately or call emergency services.`;

  if (smsBody.length > MAX_SMS_BODY_CHARS) {
    smsBody = smsBody.slice(0, MAX_SMS_BODY_CHARS);
  }

  for (const to of recipients) {
    const tw = await twilioSendSms(accountSid, authToken, to, smsBody, messagingServiceSid, fromNumber);
    if (!tw.ok) {
      console.error("panic-sms Twilio error", to, tw.status, tw.error);
      return corsJson(
        {
          code: "TWILIO_ERROR",
          error: "Failed to send one or more SMS",
          detail: tw.error,
        },
        502,
      );
    }
  }

  const { error: insErr } = await admin.from("panic_sms_dispatch").insert({
    firebase_uid: uid,
    recipient_count: recipients.length,
    request_ip: clientIp(req),
  });
  if (insErr) {
    console.warn("panic-sms audit insert failed", insErr);
  }

  return corsJson({ ok: true, sent_count: recipients.length });
});
