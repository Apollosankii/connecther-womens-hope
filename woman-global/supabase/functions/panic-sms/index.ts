import { createClient } from "npm:@supabase/supabase-js@2.49.1";
import { createRemoteJWKSet, jwtVerify } from "npm:jose@5.9.6";

/**
 * panic-sms — GBV panic SMS via Africa's Talking (production by default).
 *
 * Client → this function: JSON `{ recipients, latitude?, longitude? }` + Firebase ID token.
 * This function → Africa's Talking: `application/x-www-form-urlencoded` per official SDK
 * (see https://github.com/AfricasTalkingLtd/africastalking-node.js/blob/master/lib/sms.js).
 */

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-clerk-authorization, x-client-info, apikey, content-type, x-firebase-id-token",
};

const MAX_RECIPIENTS = 5;
const MAX_SMS_BODY_CHARS = 1400;

const DEFAULT_MAX_DISPATCHES_24H = 6;
const DEFAULT_MIN_SECONDS_BETWEEN = 180;
const DEFAULT_MAX_GLOBAL_PER_HOUR = 200;

const AT_MESSAGING_PATH = "/version1/messaging";

// ---------------------------------------------------------------------------
// HTTP helpers
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Africa's Talking — HTTP client (form POST, matches official Node SDK)
// ---------------------------------------------------------------------------

type AtRecipient = {
  number: string;
  status: string;
  statusCode?: number;
  messageId?: string;
  cost?: string;
};

function resolveAtMessagingUrl(): string {
  const custom = Deno.env.get("AFRICASTALKING_MESSAGING_URL")?.trim();
  if (custom) return custom.replace(/\/+$/, "");

  const useSandbox = /^(1|true|yes)$/i.test(Deno.env.get("AFRICASTALKING_USE_SANDBOX")?.trim() ?? "");
  if (useSandbox) return `https://api.sandbox.africastalking.com${AT_MESSAGING_PATH}`;
  return `https://api.africastalking.com${AT_MESSAGING_PATH}`;
}

function atBulkSmsMode(): string {
  return Deno.env.get("AFRICASTALKING_BULK_SMS_MODE")?.trim() || "1";
}

/** POST /version1/messaging — body is always x-www-form-urlencoded. */
async function atPostForm(
  messagingUrl: string,
  apiKey: string,
  fields: Record<string, string>,
): Promise<{ httpStatus: number; rawBody: string }> {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(fields)) {
    if (v !== undefined && v !== "") params.set(k, v);
  }

  const res = await fetch(messagingUrl, {
    method: "POST",
    headers: {
      /** Official SDK uses lowercase `apikey` as the header name. */
      apikey: apiKey,
      Accept: "application/json",
      "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
    },
    body: params.toString(),
  });

  const rawBody = await res.text();
  return { httpStatus: res.status, rawBody };
}

type AtParsedResponse = {
  errorMessage?: string;
  smsDataMessage?: string;
  recipients: AtRecipient[];
};

function parseAtMessagingJson(raw: string): AtParsedResponse {
  let root: unknown;
  try {
    root = JSON.parse(raw) as unknown;
  } catch {
    return { recipients: [], smsDataMessage: raw.slice(0, 500) };
  }

  if (!root || typeof root !== "object") return { recipients: [] };

  const o = root as Record<string, unknown>;
  if (typeof o.errorMessage === "string" && o.errorMessage.trim()) {
    return { errorMessage: o.errorMessage.trim(), recipients: [] };
  }

  const sms = o.SMSMessageData;
  if (!sms || typeof sms !== "object") return { recipients: [] };

  const sd = sms as Record<string, unknown>;
  const smsDataMessage = typeof sd.Message === "string" ? sd.Message : undefined;
  const rec = sd.Recipients;
  if (!Array.isArray(rec)) return { smsDataMessage, recipients: [] };

  const recipients: AtRecipient[] = [];
  for (const item of rec) {
    if (!item || typeof item !== "object") continue;
    const r = item as Record<string, unknown>;
    const number = typeof r.number === "string" ? r.number : "";
    const status = typeof r.status === "string" ? r.status : "";
    if (!number && !status) continue;
    recipients.push({
      number: number || "?",
      status: status || "Unknown",
      statusCode: typeof r.statusCode === "number" ? r.statusCode : undefined,
      messageId: typeof r.messageId === "string" ? r.messageId : undefined,
      cost: typeof r.cost === "string" ? r.cost : undefined,
    });
  }

  return { smsDataMessage, recipients };
}

/** Treat as delivered / accepted by AT for panic accounting. */
function atRecipientSucceeded(r: AtRecipient): boolean {
  const s = (r.status ?? "").trim().toLowerCase();
  return s === "success" || s === "sent" || s === "queued" || s === "buffered";
}

function partitionRecipients(recipients: AtRecipient[]): { sentCount: number; failures: AtRecipient[] } {
  const failures: AtRecipient[] = [];
  let sentCount = 0;
  for (const r of recipients) {
    if (atRecipientSucceeded(r)) sentCount += 1;
    else failures.push(r);
  }
  return { sentCount, failures };
}

function summarizeAllFailed(failures: AtRecipient[]): { code: string; detail: string } {
  const statuses = new Set(failures.map((f) => (f.status ?? "").trim().toLowerCase()));
  if (statuses.size === 1 && statuses.has("userinblacklist")) {
    return {
      code: "AT_RECIPIENT_BLACKLISTED",
      detail:
        "Africa's Talking marked every recipient as UserInBlacklist (opt-out / block / sender rules). " +
        "See https://help.africastalking.com/en/articles/5209677-userinblacklist — or retry without AFRICASTALKING_FROM if the block is sender-specific.",
    };
  }
  return {
    code: "TWILIO_ERROR",
    detail: failures.map((f) => `${f.number}: ${f.status} (${f.statusCode ?? ""})`).join("; "),
  };
}

/**
 * Send panic SMS end-to-end via Africa's Talking.
 * 1) Primary: one bulk request (`to` comma-separated, `bulkSMSMode=1`) — official SDK behaviour.
 * 2) If configured `from` yields all failures with only UserInBlacklist: retry bulk once without `from`.
 * 3) If bulk HTTP fails or body is unusable: fall back to one request per recipient (same form fields).
 */
async function sendPanicSmsAfricasTalking(params: {
  messagingUrl: string;
  username: string;
  apiKey: string;
  from: string | null;
  recipients: string[];
  message: string;
}): Promise<
  | { ok: true; sentCount: number; failures: AtRecipient[] }
  | { ok: false; code: string; error: string; detail: string; failures?: AtRecipient[] }
> {
  const { messagingUrl, username, apiKey, recipients, message } = params;
  let from = params.from?.trim() || null;
  const bulkMode = atBulkSmsMode();

  async function sendBulk(toCsv: string, fromVal: string | null): Promise<
    | { kind: "ok"; sentCount: number; failures: AtRecipient[] }
    | { kind: "http_fail"; httpStatus: number; raw: string }
    | { kind: "parse_empty"; raw: string }
  > {
    const fields: Record<string, string> = {
      username,
      to: toCsv,
      message,
      bulkSMSMode: bulkMode,
    };
    if (fromVal) fields.from = fromVal;

    const { httpStatus, rawBody } = await atPostForm(messagingUrl, apiKey, fields);
    if (httpStatus < 200 || httpStatus >= 300) {
      return { kind: "http_fail", httpStatus, raw: rawBody };
    }

    const parsed = parseAtMessagingJson(rawBody);
    if (parsed.errorMessage) {
      return { kind: "http_fail", httpStatus, raw: parsed.errorMessage };
    }

    const top = (parsed.smsDataMessage ?? "").toLowerCase();
    if (top.includes("invalid") && top.includes("phone")) {
      return { kind: "http_fail", httpStatus, raw: parsed.smsDataMessage ?? rawBody };
    }

    const { sentCount, failures } = partitionRecipients(parsed.recipients);
    if (parsed.recipients.length === 0) {
      return { kind: "parse_empty", raw: rawBody.slice(0, 800) };
    }
    return { kind: "ok", sentCount, failures };
  }

  async function sendOne(to: string, fromVal: string | null): Promise<
    | { kind: "ok"; sentCount: 0 | 1; failures: AtRecipient[] }
    | { kind: "http_fail"; httpStatus: number; raw: string }
  > {
    const r = await sendBulk(to, fromVal);
    if (r.kind === "http_fail") return r;
    if (r.kind === "parse_empty") return { kind: "http_fail", httpStatus: 502, raw: r.raw };
    return { kind: "ok", sentCount: r.sentCount > 0 ? 1 : 0, failures: r.failures };
  }

  const toCsv = recipients.join(",");

  // --- 1) Bulk (preferred)
  let bulk = await sendBulk(toCsv, from);
  if (bulk.kind === "ok") {
    if (bulk.sentCount === 0 && bulk.failures.length > 0 && from) {
      const onlyBl = bulk.failures.every((f) => (f.status ?? "").trim().toLowerCase() === "userinblacklist");
      if (onlyBl) {
        console.warn("panic-sms: bulk send all UserInBlacklist with from=; retrying bulk without from");
        bulk = await sendBulk(toCsv, null);
        from = null;
      }
    }
    if (bulk.kind === "ok" && bulk.sentCount > 0) {
      return { ok: true, sentCount: bulk.sentCount, failures: bulk.failures };
    }
    if (bulk.kind === "ok" && bulk.sentCount === 0 && bulk.failures.length > 0) {
      const { code, detail } = summarizeAllFailed(bulk.failures);
      return { ok: false, code, error: "Failed to send SMS to any recipient", detail, failures: bulk.failures };
    }
  }

  // --- 2) Bulk HTTP / parse failure → per-recipient fallback
  if (bulk.kind === "http_fail" || bulk.kind === "parse_empty") {
    console.warn(
      "panic-sms: bulk Africa's Talking send failed; falling back per-recipient",
      bulk.kind === "http_fail" ? { httpStatus: bulk.httpStatus, raw: bulk.raw.slice(0, 400) } : { raw: bulk.raw.slice(0, 400) },
    );
  }

  const failures: AtRecipient[] = [];
  let sentCount = 0;

  for (const to of recipients) {
    let one = await sendOne(to, from);
    if (one.kind === "http_fail") {
      return {
        ok: false,
        code: "TWILIO_ERROR",
        error: "Failed to send SMS",
        detail: one.raw.slice(0, 800),
        failures,
      };
    }
    if (one.sentCount === 0 && one.failures.length && from) {
      const onlyBl = one.failures.every((f) => (f.status ?? "").trim().toLowerCase() === "userinblacklist");
      if (onlyBl) {
        one = await sendOne(to, null);
      }
    }
    if (one.kind === "http_fail") {
      return {
        ok: false,
        code: "TWILIO_ERROR",
        error: "Failed to send SMS",
        detail: one.raw.slice(0, 800),
        failures,
      };
    }
    if (one.sentCount > 0) {
      sentCount += 1;
    } else {
      failures.push(...one.failures);
    }
  }

  if (sentCount === 0) {
    const { code, detail } = summarizeAllFailed(failures);
    return { ok: false, code, error: "Failed to send SMS to any recipient", detail, failures };
  }

  return { ok: true, sentCount, failures };
}

// ---------------------------------------------------------------------------
// Edge handler
// ---------------------------------------------------------------------------

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return corsJson({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();

  if (!supabaseUrl || !serviceKey) {
    return corsJson({ code: "SERVER_MISCONFIG", error: "Supabase URL or service role missing" }, 500);
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

  let requestBody: { recipients?: unknown; latitude?: unknown; longitude?: unknown };
  try {
    requestBody = (await req.json()) as typeof requestBody;
  } catch {
    return corsJson({ error: "Invalid JSON body" }, 400);
  }

  const rawRecipients = Array.isArray(requestBody.recipients) ? requestBody.recipients : [];
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

  const lat =
    typeof requestBody.latitude === "number" && Number.isFinite(requestBody.latitude) ? requestBody.latitude : null;
  const lng =
    typeof requestBody.longitude === "number" && Number.isFinite(requestBody.longitude) ? requestBody.longitude : null;

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
        "panic_sms_max_dispatches_per_24h, panic_sms_min_seconds_between, panic_sms_max_global_per_hour, panic_sms_twilio_enabled",
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

  if (settingsErr) console.warn("panic-sms platform_settings read", settingsErr);
  if (globalErr) console.warn("panic-sms global rate count", globalErr);
  if (userDayErr) console.warn("panic-sms user rate count", userDayErr);
  if (lastErr) console.warn("panic-sms last dispatch", lastErr);

  const s = settingsRow as Record<string, unknown> | null;
  const platformSmsEnabled = s?.panic_sms_twilio_enabled !== false;

  const maxPerUser24h = Math.max(
    1,
    Math.min(
      200,
      Number(s?.panic_sms_max_dispatches_per_24h ?? DEFAULT_MAX_DISPATCHES_24H) || DEFAULT_MAX_DISPATCHES_24H,
    ),
  );
  const minSecondsBetween = Math.max(
    0,
    Math.min(
      86400,
      Number(s?.panic_sms_min_seconds_between ?? DEFAULT_MIN_SECONDS_BETWEEN) || DEFAULT_MIN_SECONDS_BETWEEN,
    ),
  );
  const maxGlobalPerHour = Math.max(
    10,
    Math.min(
      100_000,
      Number(s?.panic_sms_max_global_per_hour ?? DEFAULT_MAX_GLOBAL_PER_HOUR) || DEFAULT_MAX_GLOBAL_PER_HOUR,
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

  if (!platformSmsEnabled) {
    return corsJson(
      {
        code: "TWILIO_DISABLED",
        error: "ConnectHer automated panic SMS is turned off. Use SMS from your device.",
      },
      403,
    );
  }

  const atUsername = Deno.env.get("AFRICASTALKING_USERNAME")?.trim();
  const atApiKey = Deno.env.get("AFRICASTALKING_API_KEY")?.trim();
  const atFrom = Deno.env.get("AFRICASTALKING_FROM")?.trim() || null;

  if (!atUsername || !atApiKey) {
    console.error("panic-sms: AFRICASTALKING_USERNAME or AFRICASTALKING_API_KEY missing");
    return corsJson(
      {
        code: "SERVER_MISCONFIG",
        error: "Africa's Talking not configured",
        detail: "Set AFRICASTALKING_USERNAME and AFRICASTALKING_API_KEY on this Edge Function.",
      },
      500,
    );
  }

  const messagingUrl = resolveAtMessagingUrl();
  console.info("panic-sms: Africa's Talking", { messagingUrl, bulkSMSMode: atBulkSmsMode(), recipientCount: recipients.length });
  if (atUsername.toLowerCase() === "sandbox" && !messagingUrl.includes("sandbox")) {
    console.warn(
      "panic-sms: username is 'sandbox' but host is not sandbox — use live credentials for production SMS.",
    );
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

  const sendResult = await sendPanicSmsAfricasTalking({
    messagingUrl,
    username: atUsername,
    apiKey: atApiKey,
    from: atFrom,
    recipients,
    message: smsBody,
  });

  if (!sendResult.ok) {
    console.error("panic-sms: Africa's Talking send failed", sendResult.code, sendResult.detail);
    return corsJson(
      {
        code: sendResult.code,
        error: sendResult.error,
        detail: sendResult.detail,
        ...(sendResult.failures?.length ? { failed_recipients: sendResult.failures } : {}),
      },
      502,
    );
  }

  const { error: insErr } = await admin.from("panic_sms_dispatch").insert({
    firebase_uid: uid,
    recipient_count: sendResult.sentCount,
    request_ip: clientIp(req),
  });
  if (insErr) console.warn("panic-sms audit insert failed", insErr);

  const responseJson: Record<string, unknown> = { ok: true, sent_count: sendResult.sentCount };
  if (sendResult.failures.length > 0) {
    responseJson.partial = true;
    responseJson.failed_recipients = sendResult.failures;
  }
  return corsJson(responseJson);
});
