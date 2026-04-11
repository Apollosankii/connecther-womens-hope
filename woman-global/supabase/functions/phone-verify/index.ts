import { createClient } from "npm:@supabase/supabase-js@2.49.1";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@5.9.6";

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-clerk-authorization, x-client-info, apikey, content-type, x-firebase-id-token",
};

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

/** Firebase ID token: prefer dedicated header when `Authorization` carries the Supabase anon key (gateway). */
function firebaseIdTokenFromRequest(req: Request): string | null {
  const x = req.headers.get("X-Firebase-Id-Token") ?? req.headers.get("x-firebase-id-token");
  if (x) return x.replace(/^Bearer\s+/i, "").trim();
  return bearerToken(req);
}

/** Decode JWT payload without verify (issuer check only). */
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

async function sha256Base36(input: string, length = 10): Promise<string> {
  const bytes = new TextEncoder().encode(input);
  const hash = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
  let n = 0n;
  for (let i = 0; i < 8; i++) n = (n << 8n) | BigInt(hash[i]);
  return n.toString(36).padStart(length, "0").slice(0, length);
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

function twilioBasicAuth(accountSid: string, authToken: string): string {
  const raw = `${accountSid}:${authToken}`;
  return `Basic ${btoa(raw)}`;
}

async function twilioVerifyStart(
  accountSid: string,
  authToken: string,
  serviceSid: string,
  to: string,
): Promise<{ ok: boolean; error?: string; status?: number }> {
  const url = `https://verify.twilio.com/v2/Services/${encodeURIComponent(serviceSid)}/Verifications`;
  const body = new URLSearchParams({ To: to, Channel: "sms" });
  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: twilioBasicAuth(accountSid, authToken),
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: body.toString(),
  });
  if (!res.ok) {
    const t = await res.text();
    return { ok: false, error: t || res.statusText, status: res.status };
  }
  return { ok: true };
}

async function twilioVerifyCheck(
  accountSid: string,
  authToken: string,
  serviceSid: string,
  to: string,
  code: string,
): Promise<{ approved: boolean; error?: string; status?: number }> {
  const url = `https://verify.twilio.com/v2/Services/${encodeURIComponent(serviceSid)}/VerificationCheck`;
  const body = new URLSearchParams({ To: to, Code: code });
  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: twilioBasicAuth(accountSid, authToken),
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: body.toString(),
  });
  const raw = await res.text();
  let json: { status?: string; message?: string } = {};
  try {
    json = JSON.parse(raw) as { status?: string; message?: string };
  } catch {
    /* non-JSON body */
  }
  if (!res.ok) {
    return { approved: false, error: json.message || raw || res.statusText, status: res.status };
  }
  return { approved: json.status === "approved" };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: { ...corsHeaders } });
  }
  if (req.method !== "POST") {
    return corsJson({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  const accountSid = Deno.env.get("TWILIO_ACCOUNT_SID")?.trim();
  const authToken = Deno.env.get("TWILIO_AUTH_TOKEN")?.trim();
  const serviceSid = Deno.env.get("TWILIO_VERIFY_SERVICE_SID")?.trim();

  if (!supabaseUrl || !serviceKey) {
    return corsJson({ code: "SERVER_MISCONFIG", error: "Supabase URL or service role missing" }, 500);
  }
  if (!accountSid || !authToken || !serviceSid) {
    console.error("phone-verify: Twilio secrets missing (TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN / TWILIO_VERIFY_SERVICE_SID)");
    return corsJson(
      {
        code: "SERVER_MISCONFIG",
        error: "Twilio not configured",
        detail: "Set Edge secrets: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_VERIFY_SERVICE_SID (Verify Service SID starts with VA).",
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
        detail: "Send header X-Firebase-Id-Token: <firebase_id_token> (or legacy Authorization: Bearer <firebase_id_token> if not using Supabase anon in Authorization).",
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
        error: "Authorization carries a non-Firebase JWT (e.g. Supabase anon).",
        detail: `Token issuer: ${iss}. Send Firebase ID token in header X-Firebase-Id-Token and keep Authorization as Bearer <Supabase anon key>.`,
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
        hint: "Confirm Edge secret FIREBASE_PROJECT_ID matches the Firebase project used by the app (google-services.json).",
      },
      401,
    );
  }

  let body: { action?: string; phone?: string; code?: string };
  try {
    body = (await req.json()) as { action?: string; phone?: string; code?: string };
  } catch {
    return corsJson({ error: "Invalid JSON body" }, 400);
  }

  const action = (body.action ?? "").trim().toLowerCase();
  const phone = (body.phone ?? "").trim();
  if (!isValidE164(phone)) {
    return corsJson({ code: "INVALID_PHONE", error: "Phone must be E.164 (e.g. +254712345678)" }, 400);
  }

  const admin = createClient(supabaseUrl, serviceKey);

  /** Rate limit: max 5 starts per phone per rolling hour. */
  async function countStartsLastHour(): Promise<number> {
    const since = new Date(Date.now() - 60 * 60 * 1000).toISOString();
    const { count, error } = await admin
      .from("phone_verify_events")
      .select("id", { count: "exact", head: true })
      .eq("e164", phone)
      .eq("event", "start")
      .gte("created_at", since);
    if (error) {
      console.warn("phone-verify rate count error", error);
      return 0;
    }
    return count ?? 0;
  }

  async function logEvent(event: "start" | "check" | "check_failed"): Promise<void> {
    const { error } = await admin.from("phone_verify_events").insert({
      firebase_uid: uid,
      e164: phone,
      event,
    });
    if (error) console.warn("phone-verify log insert", error);
  }

  if (action === "start") {
    const n = await countStartsLastHour();
    if (n >= 5) {
      return corsJson({ code: "RATE_LIMIT", error: "Too many verification attempts for this number" }, 429);
    }

    const tw = await twilioVerifyStart(accountSid, authToken, serviceSid, phone);
    if (!tw.ok) {
      console.error("Twilio start failed", tw.status, tw.error);
      return corsJson({ code: "TWILIO_ERROR", error: "Failed to send verification", detail: tw.error }, 502);
    }
    await logEvent("start");
    return corsJson({ ok: true });
  }

  if (action === "check") {
    const code = (body.code ?? "").trim();
    if (code.length < 4 || code.length > 10) {
      return corsJson({ code: "INVALID_CODE", error: "Invalid code" }, 400);
    }

    const tw = await twilioVerifyCheck(accountSid, authToken, serviceSid, phone, code);
    if (!tw.approved) {
      await logEvent("check_failed");
      return corsJson(
        { ok: false, verified: false, error: tw.error || "Verification failed" },
        tw.status && tw.status >= 400 && tw.status < 500 ? 400 : 400,
      );
    }

    const { data: conflict } = await admin
      .from("users")
      .select("id")
      .eq("phone", phone)
      .neq("clerk_user_id", uid)
      .maybeSingle();

    if (conflict?.id != null) {
      return corsJson({ code: "PHONE_IN_USE", error: "This phone number is already used by another account" }, 409);
    }

    const nowIso = new Date().toISOString();
    const { data: existing, error: exErr } = await admin
      .from("users")
      .select("id, clerk_user_id")
      .eq("clerk_user_id", uid)
      .maybeSingle();

    if (exErr) {
      return corsJson({ error: "Database lookup failed", detail: exErr.message }, 500);
    }

    if (existing?.id != null) {
      const { error: upErr } = await admin
        .from("users")
        .update({ phone, phone_verified_at: nowIso })
        .eq("clerk_user_id", uid);
      if (upErr) {
        return corsJson({ error: "Failed to save phone", detail: upErr.message }, 500);
      }
    } else {
      const userId = `usr_${await sha256Base36(uid, 12)}`;
      const { error: insErr } = await admin.from("users").insert({
        clerk_user_id: uid,
        user_id: userId,
        title: "Ms",
        first_name: "ConnectHer",
        last_name: "User",
        phone,
        phone_verified_at: nowIso,
        password: `firebase:${uid}`,
      });
      if (insErr) {
        console.error("phone-verify user insert", insErr);
        return corsJson({ error: "Failed to create user row", detail: insErr.message }, 500);
      }
    }

    await logEvent("check");
    return corsJson({ ok: true, verified: true });
  }

  return corsJson({ error: "Unknown action; use start or check" }, 400);
});
