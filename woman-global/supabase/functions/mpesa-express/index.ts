// mpesa-express: M-Pesa STK (Lipa Na M-Pesa Online). Same URL: app JSON { plan_id, phone } + Clerk Bearer,
// or Safaricom callback JSON with Body.stkCallback. Daraja: OAuth then POST /mpesa/stkpush/v1/processrequest.
// Secrets: MPESA_CONSUMER_KEY, MPESA_CONSUMER_SECRET, MPESA_SHORTCODE, MPESA_PASSKEY; optional MPESA_CALLBACK_TOKEN, DARAJA_BASE_URL.
// Deploy with verify_jwt false (config.toml + supabase functions deploy mpesa-express --no-verify-jwt) so Clerk JWT is accepted.
import { createClient } from "npm:@supabase/supabase-js@2.49.1";

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-clerk-authorization, x-client-info, apikey, content-type",
};

function corsJson(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

// Decode JWT payload; UTF-8-safe middle-segment parse (atob plus JSON.parse mishandles some Unicode).
function jwtPayload(jwt: string): Record<string, unknown> | null {
  const parts = jwt.split(".");
  if (parts.length < 2) return null;
  try {
    const pad = parts[1].length % 4 === 0 ? "" : "=".repeat(4 - (parts[1].length % 4));
    const b64 = parts[1].replace(/-/g, "+").replace(/_/g, "/") + pad;
    const binary = atob(b64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    const text = new TextDecoder().decode(bytes);
    return JSON.parse(text) as Record<string, unknown>;
  } catch {
    return null;
  }
}

function getClerkSubFromJwt(authHeader: string | null): string | null {
  const m = authHeader?.match(/^Bearer\s+(.+)$/i);
  if (!m) return null;
  const token = m[1].trim();
  const payload = jwtPayload(token);
  return payload && typeof payload.sub === "string" ? payload.sub : null;
}

// Clerk JWT: Authorization header, or X-Clerk-Authorization fallback.
function clerkBearerSource(req: Request): string | null {
  const a = req.headers.get("Authorization") ?? req.headers.get("authorization");
  if (a?.match(/^Bearer\s+/i)) return a;
  const x = req.headers.get("X-Clerk-Authorization") ?? req.headers.get("x-clerk-authorization");
  if (x?.match(/^Bearer\s+/i)) return x;
  return null;
}

function darajaTimestamp(): string {
  const d = new Date();
  const p = (n: number) => n.toString().padStart(2, "0");
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
}

function normalizeKenyaPhone(input: string): string | null {
  const digits = input.replace(/\D/g, "");
  if (digits.length === 12 && digits.startsWith("254")) return digits;
  if (digits.length === 10 && digits.startsWith("0")) return "254" + digits.slice(1);
  if (digits.length === 9 && digits.startsWith("7")) return "254" + digits;
  return null;
}

function isDarajaStkCallbackBody(parsed: unknown): parsed is {
  Body: { stkCallback: Record<string, unknown> };
} {
  if (!parsed || typeof parsed !== "object") return false;
  const body = (parsed as Record<string, unknown>).Body;
  if (!body || typeof body !== "object") return false;
  const cb = (body as Record<string, unknown>).stkCallback;
  return cb !== null && typeof cb === "object";
}

let darajaOAuthCache: {
  token: string;
  expiresAtMs: number;
  base: string;
  key: string;
  secret: string;
} | null = null;

const OAUTH_REFRESH_BUFFER_MS = 5 * 60 * 1000;
/** Daraja can be slow from some regions; avoid Supabase wall-clock abort before we get a response. */
const DARAJA_FETCH_TIMEOUT_MS = 45_000;

function darajaFetchAbortSignal(ms: number): AbortSignal {
  const T = AbortSignal as unknown as { timeout?: (n: number) => AbortSignal };
  if (typeof T.timeout === "function") return T.timeout(ms);
  const c = new AbortController();
  setTimeout(() => c.abort(), ms);
  return c.signal;
}

class DarajaOAuthError extends Error {
  constructor(
    message: string,
    public readonly kind: "http" | "network" | "parse",
    public readonly httpStatus?: number,
    public readonly bodySnippet?: string,
  ) {
    super(message);
    this.name = "DarajaOAuthError";
  }
}

/** Basic auth: UTF-8 safe (plain btoa(key:secret) throws if key/secret ever contain non-Latin1). */
function basicAuthBase64(key: string, secret: string): string {
  const raw = `${key}:${secret}`;
  const bytes = new TextEncoder().encode(raw);
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin);
}

async function getDarajaAccessToken(base: string, key: string, secret: string): Promise<string> {
  const baseNorm = base.replace(/\/$/, "");
  const now = Date.now();
  if (
    darajaOAuthCache &&
    darajaOAuthCache.base === baseNorm &&
    darajaOAuthCache.key === key &&
    darajaOAuthCache.secret === secret &&
    darajaOAuthCache.expiresAtMs > now + OAUTH_REFRESH_BUFFER_MS
  ) {
    return darajaOAuthCache.token;
  }

  let creds: string;
  try {
    creds = basicAuthBase64(key, secret);
  } catch {
    throw new DarajaOAuthError("Could not build Basic auth for Daraja (check MPESA_CONSUMER_KEY / SECRET)", "parse");
  }

  const oauthUrl = `${baseNorm}/oauth/v1/generate?grant_type=client_credentials`;
  let res: Response;
  try {
    res = await fetch(oauthUrl, {
      headers: { Authorization: `Basic ${creds}` },
      signal: darajaFetchAbortSignal(DARAJA_FETCH_TIMEOUT_MS),
    });
  } catch (e) {
    const name = e instanceof Error ? e.name : "";
    const msg = e instanceof Error ? e.message : String(e);
    if (name === "TimeoutError" || name === "AbortError" || /abort|timeout/i.test(msg)) {
      throw new DarajaOAuthError(
        `Daraja OAuth timed out after ${DARAJA_FETCH_TIMEOUT_MS}ms (check DARAJA_BASE_URL and network)`,
        "network",
      );
    }
    throw new DarajaOAuthError(`Daraja OAuth fetch failed: ${msg}`, "network");
  }

  if (!res.ok) {
    const t = await res.text();
    throw new DarajaOAuthError(`Daraja OAuth HTTP ${res.status}`, "http", res.status, t.slice(0, 800));
  }

  let j: { access_token?: string; expires_in?: string | number };
  try {
    j = (await res.json()) as { access_token?: string; expires_in?: string | number };
  } catch {
    throw new DarajaOAuthError("Daraja OAuth: response was not JSON", "parse", res.status);
  }

  if (!j.access_token) {
    throw new DarajaOAuthError("Daraja OAuth: no access_token in JSON body", "parse", res.status);
  }

  const expiresInSec = Math.max(60, Number(j.expires_in) || 3600);
  darajaOAuthCache = {
    token: j.access_token,
    expiresAtMs: now + expiresInSec * 1000,
    base: baseNorm,
    key,
    secret,
  };
  return j.access_token;
}

function addMonths(d: Date, n: number): Date {
  const x = new Date(d);
  x.setMonth(x.getMonth() + n);
  return x;
}

function addYears(d: Date, n: number): Date {
  const x = new Date(d);
  x.setFullYear(x.getFullYear() + n);
  return x;
}

function fmtDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

async function handleSafaricomCallback(
  req: Request,
  payload: { Body: { stkCallback: Record<string, unknown> } },
): Promise<Response> {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!supabaseUrl || !serviceKey) {
    return new Response(JSON.stringify({ ResultCode: 1, ResultDesc: "Server misconfigured" }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }

  const url = new URL(req.url);
  const queryToken = url.searchParams.get("token") || "";
  const envStaticToken = Deno.env.get("MPESA_CALLBACK_TOKEN")?.trim() || "";

  const cb = payload.Body.stkCallback;
  const checkoutId = cb?.CheckoutRequestID as string | undefined;
  const resultCode = Number(cb?.ResultCode ?? -1);
  const resultDesc = String(cb?.ResultDesc ?? "");

  if (!checkoutId) {
    return new Response(JSON.stringify({ ResultCode: 0, ResultDesc: "Ignored" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }

  const admin = createClient(supabaseUrl, serviceKey);

  const { data: row, error: fetchErr } = await admin
    .from("mpesa_stk_payments")
    .select("id, user_id, plan_id, amount, status, callback_verifier")
    .eq("checkout_request_id", checkoutId)
    .maybeSingle();

  if (fetchErr || !row) {
    console.error("mpesa-express callback: row not found", checkoutId, fetchErr);
    return new Response(JSON.stringify({ ResultCode: 0, ResultDesc: "OK" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }

  if (envStaticToken) {
    if (queryToken !== envStaticToken) {
      return new Response(JSON.stringify({ ResultCode: 1, ResultDesc: "Unauthorized" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }
  } else {
    const expected = row.callback_verifier;
    if (!expected || queryToken !== expected) {
      return new Response(JSON.stringify({ ResultCode: 1, ResultDesc: "Unauthorized" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }
  }

  if (row.status === "completed") {
    return new Response(JSON.stringify({ ResultCode: 0, ResultDesc: "OK" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }

  let mpesaReceipt = "";
  const meta = cb?.CallbackMetadata as { Item?: { Name?: string; Value?: unknown }[] } | undefined;
  if (meta?.Item) {
    for (const it of meta.Item) {
      if (it.Name === "MpesaReceiptNumber" && it.Value != null) {
        mpesaReceipt = String(it.Value);
        break;
      }
    }
  }

  await admin
    .from("mpesa_stk_payments")
    .update({
      status: resultCode === 0 ? "completed" : "failed",
      result_code: resultCode,
      result_desc: resultDesc,
      mpesa_receipt_number: mpesaReceipt || null,
      raw_callback: payload as unknown as Record<string, unknown>,
      updated_at: new Date().toISOString(),
    })
    .eq("id", row.id);

  if (resultCode !== 0) {
    return new Response(JSON.stringify({ ResultCode: 0, ResultDesc: "OK" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }

  const { data: plan, error: planErr } = await admin
    .from("subscription_plans")
    .select("duration_type, duration_value, connects_limit_enabled, connects_per_period")
    .eq("id", row.plan_id)
    .maybeSingle();

  if (planErr || !plan) {
    console.error("mpesa-express callback: plan missing", row.plan_id, planErr);
    return new Response(JSON.stringify({ ResultCode: 0, ResultDesc: "OK" }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  }

  const started = new Date();
  let expires = started;
  const dtype = String(plan.duration_type || "month").toLowerCase();
  const dval = Math.max(1, Number(plan.duration_value) || 1);
  if (dtype === "year") expires = addYears(started, dval);
  else expires = addMonths(started, dval);

  await admin
    .from("user_plan_subscriptions")
    .update({ status: "expired", updated_at: new Date().toISOString() })
    .eq("user_id", row.user_id)
    .eq("status", "active");

  const insertSub: Record<string, unknown> = {
    user_id: row.user_id,
    plan_id: row.plan_id,
    status: "active",
    started_at: fmtDate(started),
    expires_at: fmtDate(expires),
    payment_reference: mpesaReceipt || checkoutId,
    notes: "M-Pesa STK (Daraja)",
  };
  const limitOn = plan.connects_limit_enabled === true;
  const perRaw = plan.connects_per_period;
  const perNum = perRaw != null ? Number(perRaw) : NaN;
  if (limitOn && Number.isFinite(perNum) && perNum >= 0) {
    insertSub.connects_granted = Math.floor(perNum);
    insertSub.connects_used = 0;
    insertSub.connects_period_started_at = fmtDate(started);
  }

  const { error: subErr } = await admin.from("user_plan_subscriptions").insert(insertSub);

  if (subErr) console.error("mpesa-express callback: subscription insert failed", subErr);

  return new Response(JSON.stringify({ ResultCode: 0, ResultDesc: "OK" }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

async function handleAppInitiate(req: Request, parsed: Record<string, unknown>): Promise<Response> {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  const consumerKey = Deno.env.get("MPESA_CONSUMER_KEY")?.trim();
  const consumerSecret = Deno.env.get("MPESA_CONSUMER_SECRET")?.trim();
  const shortcode = Deno.env.get("MPESA_SHORTCODE")?.trim();
  const passkey = Deno.env.get("MPESA_PASSKEY")?.trim();
  const staticCallbackSecret = Deno.env.get("MPESA_CALLBACK_TOKEN")?.trim();
  const base = (Deno.env.get("DARAJA_BASE_URL")?.trim() || "https://sandbox.safaricom.co.ke").replace(/\/$/, "");

  if (!supabaseUrl || !serviceKey || !consumerKey || !consumerSecret || !shortcode || !passkey) {
    return corsJson(
      {
        code: "MISSING_SECRETS",
        error:
          "Set MPESA_CONSUMER_KEY, MPESA_CONSUMER_SECRET, MPESA_SHORTCODE, MPESA_PASSKEY. MPESA_CALLBACK_TOKEN is optional.",
      },
      500,
    );
  }

  // Do not decode/validate SUPABASE_SERVICE_ROLE_KEY here: other functions rely on the same secret;
  // a failed JWT parse (encoding edge cases) falsely triggered INVALID_SERVICE_ROLE_KEY. Wrong key
  // surfaces as PostgREST/RLS errors from createClient instead.

  const clerkSub = getClerkSubFromJwt(clerkBearerSource(req));
  if (!clerkSub) {
    return corsJson(
      {
        code: "CLERK_JWT_REQUIRED",
        error:
          "Missing or invalid Clerk Bearer token. Ensure this function is deployed with verify_jwt disabled (see supabase/config.toml and --no-verify-jwt).",
      },
      401,
    );
  }

  const planId = typeof parsed.plan_id === "number"
    ? parsed.plan_id
    : parseInt(String(parsed.plan_id ?? ""), 10);
  if (!planId || planId < 1) return corsJson({ error: "plan_id required" }, 400);

  const rawPhone = parsed.phone;
  if (!rawPhone || typeof rawPhone !== "string") {
    return corsJson({ error: "phone required (Safaricom number)" }, 400);
  }
  const phone = normalizeKenyaPhone(rawPhone);
  if (!phone) {
    return corsJson({ error: "Invalid phone. Use 07XXXXXXXX, 7XXXXXXXX, or 2547XXXXXXXX" }, 400);
  }

  const admin = createClient(supabaseUrl, serviceKey);

  const { data: userRow, error: userErr } = await admin
    .from("users")
    .select("id")
    .eq("clerk_user_id", clerkSub)
    .maybeSingle();
  if (userErr || !userRow?.id) {
    return corsJson({ error: "User not found for this session" }, 404);
  }

  const { data: plan, error: planErr } = await admin
    .from("subscription_plans")
    .select("id, price, currency, is_active, name")
    .eq("id", planId)
    .maybeSingle();
  if (planErr || !plan || !plan.is_active) {
    return corsJson({ error: "Plan not found or inactive" }, 404);
  }
  if (String(plan.currency || "KES").toUpperCase() !== "KES") {
    return corsJson({ error: "Only KES plans supported for M-Pesa" }, 400);
  }
  const amount = Math.round(Number(plan.price));
  if (amount < 1) return corsJson({ error: "Invalid plan amount" }, 400);

  const ts = darajaTimestamp();
  const password = btoa(`${shortcode}${passkey}${ts}`);

  let access: string;
  try {
    access = await getDarajaAccessToken(base, consumerKey, consumerSecret);
  } catch (e) {
    console.error("mpesa-express Daraja OAuth:", e);
    if (e instanceof DarajaOAuthError) {
      return corsJson(
        {
          code: "DARAJA_OAUTH_FAILED",
          error: "Could not reach M-Pesa (auth). Try again later.",
          kind: e.kind,
          daraja_http_status: e.httpStatus,
          daraja_detail: e.bodySnippet,
          daraja_base_url: base,
          hint:
            e.kind === "http" && e.httpStatus === 401
              ? "Sandbox credentials must match sandbox app in Daraja; production URL needs production keys. Unset DARAJA_BASE_URL for default sandbox."
              : e.kind === "network"
                ? "Timeout or TLS/DNS from Edge to Safaricom — retry, or confirm sandbox.safaricom.co.ke is reachable."
                : undefined,
        },
        502,
      );
    }
    return corsJson(
      {
        code: "DARAJA_OAUTH_FAILED",
        error: "Could not reach M-Pesa (auth). Try again later.",
        detail: e instanceof Error ? e.message : String(e),
      },
      502,
    );
  }

  const callbackQueryToken = staticCallbackSecret || access;
  const functionSlug = "mpesa-express";
  const callbackUrl =
    `${supabaseUrl}/functions/v1/${functionSlug}?token=${encodeURIComponent(callbackQueryToken)}`;

  const stkBody = {
    BusinessShortCode: shortcode,
    Password: password,
    Timestamp: ts,
    TransactionType: "CustomerPayBillOnline",
    Amount: amount,
    PartyA: phone,
    PartyB: shortcode,
    PhoneNumber: phone,
    CallBackURL: callbackUrl,
    AccountReference: `SUB-${planId}`.slice(0, 12),
    TransactionDesc: `ConnectHer ${plan.name ?? "sub"}`.slice(0, 13),
  };

  const stkRes = await fetch(`${base}/mpesa/stkpush/v1/processrequest`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${access}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(stkBody),
  });

  const stkJson = await stkRes.json().catch(() => ({}));
  if (!stkRes.ok) {
    console.error("STK HTTP", stkRes.status, stkJson);
    return corsJson({ error: "M-Pesa STK request failed", detail: stkJson }, 502);
  }

  const merchantId = stkJson?.MerchantRequestID as string | undefined;
  const checkoutId = stkJson?.CheckoutRequestID as string | undefined;
  const responseCode = stkJson?.ResponseCode as string | undefined;
  const responseDesc = stkJson?.ResponseDescription as string | undefined;

  if (responseCode !== "0" || !checkoutId) {
    return corsJson(
      { error: responseDesc || "STK not accepted", responseCode, detail: stkJson },
      400,
    );
  }

  const { error: insErr } = await admin.from("mpesa_stk_payments").insert({
    user_id: userRow.id,
    plan_id: planId,
    amount,
    phone_normalized: phone,
    checkout_request_id: checkoutId,
    merchant_request_id: merchantId ?? null,
    status: "pending",
    callback_verifier: callbackQueryToken,
  });

  if (insErr) {
    console.error("insert mpesa_stk_payments", insErr);
    return corsJson(
      {
        code: "DB_INSERT_FAILED",
        error: "Could not record payment (try again)",
        detail: insErr.message,
      },
      500,
    );
  }

  return corsJson({
    ok: true,
    customerMessage: stkJson?.CustomerMessage ?? "Check your phone for the M-Pesa prompt.",
    checkoutRequestId: checkoutId,
    merchantRequestId: merchantId,
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return corsJson({ error: "Method not allowed" }, 405);
  }

  let parsed: unknown;
  try {
    const text = await req.text();
    parsed = text ? JSON.parse(text) : {};
  } catch {
    return corsJson({ error: "Invalid JSON" }, 400);
  }

  if (isDarajaStkCallbackBody(parsed)) {
    return handleSafaricomCallback(req, parsed);
  }

  if (parsed && typeof parsed === "object") {
    return handleAppInitiate(req, parsed as Record<string, unknown>);
  }

  return corsJson({ error: "Invalid request body" }, 400);
});
