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

function htmlPage(title: string, message: string) {
  return new Response(
    `<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${title}</title>
  </head>
  <body style="font-family: system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial; padding: 24px;">
    <h2 style="margin-top: 0;">${title}</h2>
    <p>${message}</p>
    <p style="color:#666; font-size: 13px;">You can close this tab and return to the ConnectHer app. Your subscription will activate automatically after Paystack confirmation.</p>
  </body>
</html>`,
    {
      headers: { "Content-Type": "text/html; charset=utf-8", ...corsHeaders },
    },
  );
}

// Decode JWT payload; UTF-8-safe middle-segment parse (same approach as mpesa-express).
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

function clerkBearerSource(req: Request): string | null {
  const a = req.headers.get("Authorization") ?? req.headers.get("authorization");
  if (a?.match(/^Bearer\s+/i)) return a;
  const x = req.headers.get("X-Clerk-Authorization") ?? req.headers.get("x-clerk-authorization");
  if (x?.match(/^Bearer\s+/i)) return x;
  return null;
}

function fmtDate(d: Date): string {
  return d.toISOString().slice(0, 10);
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

async function hmacSha512Hex(secret: string, data: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-512" },
    false,
    ["sign"],
  );

  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(data));
  const bytes = new Uint8Array(sig);
  // hex lowercase (matches Paystack docs)
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function isPaystackWebhookEvent(payload: unknown): payload is { event: string; data: Record<string, unknown> } {
  return !!payload && typeof payload === "object" && "event" in payload && typeof (payload as any).event === "string";
}

async function handleInitialize(req: Request, parsed: Record<string, unknown>) {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  const paystackSecretKey = Deno.env.get("PAYSTACK_SECRET_KEY")?.trim();

  if (!supabaseUrl) return corsJson({ error: "SUPABASE_URL missing" }, 500);
  if (!serviceKey) return corsJson({ error: "SUPABASE_SERVICE_ROLE_KEY missing" }, 500);
  if (!paystackSecretKey) return corsJson({ error: "PAYSTACK_SECRET_KEY missing" }, 500);

  const clerkSub = getClerkSubFromJwt(clerkBearerSource(req));
  if (!clerkSub) {
    return corsJson({ code: "CLERK_JWT_REQUIRED", error: "Missing or invalid Clerk Bearer token" }, 401);
  }

  const rawPlanId = parsed.plan_id;
  const planId = typeof rawPlanId === "number" ? rawPlanId : parseInt(String(rawPlanId ?? ""), 10);
  if (!planId || planId < 1) return corsJson({ error: "plan_id required" }, 400);

  const emailRaw = parsed.email;
  const email = typeof emailRaw === "string" ? emailRaw.trim() : "";
  if (!email) return corsJson({ error: "email required" }, 400);

  const admin = createClient(supabaseUrl, serviceKey);

  const { data: userRow, error: userErr } = await admin
    .from("users")
    .select("id")
    .eq("clerk_user_id", clerkSub)
    .maybeSingle();
  if (userErr || !userRow?.id) return corsJson({ error: "User not found for this session" }, 404);

  const { data: plan, error: planErr } = await admin
    .from("subscription_plans")
    .select("id, price, currency, is_active, name, duration_type, duration_value, connects_limit_enabled, connects_per_period")
    .eq("id", planId)
    .maybeSingle();

  if (planErr || !plan || !plan.is_active) return corsJson({ error: "Plan not found or inactive" }, 404);

  const priceNum = Number(plan.price);
  if (!Number.isFinite(priceNum) || priceNum <= 0) return corsJson({ error: "Invalid plan price" }, 400);

  // Paystack expects smallest currency unit (kobo for KES)
  const amountKobo = Math.round(priceNum * 100);
  if (amountKobo < 1) return corsJson({ error: "Invalid amount" }, 400);

  const reference = `PAY-${String(userRow.id).slice(0, 8)}-${planId}-${crypto.randomUUID()}`.slice(0, 36);
  const redirectCallbackUrl = `${supabaseUrl}/functions/v1/paystack-express?redirect=1`;

  // Track the checkout attempt so webhook can activate the correct plan.
  const { error: txErr } = await admin.from("paystack_transactions").insert({
    user_id: userRow.id,
    plan_id: planId,
    amount_kobo: amountKobo,
    currency: String(plan.currency || "KES").toUpperCase(),
    email,
    reference,
    status: "pending",
  });
  if (txErr) return corsJson({ error: "Failed to create payment record", detail: txErr.message }, 500);

  const paystackBase = (Deno.env.get("PAYSTACK_BASE_URL")?.trim() || "https://api.paystack.co").replace(/\/$/, "");
  const initUrl = `${paystackBase}/transaction/initialize`;

  const initBody = {
    email,
    amount: amountKobo,
    reference,
    callback_url: redirectCallbackUrl,
    metadata: JSON.stringify({ plan_id: planId, user_id: userRow.id, plan_name: plan.name ?? null }),
  };

  const initResp = await fetch(initUrl, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${paystackSecretKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(initBody),
  });

  const initJson = (await initResp.json().catch(() => ({}))) as any;
  if (!initResp.ok) {
    console.error("paystack initialize failed", initResp.status, initJson);
    return corsJson({ error: "Paystack initialize failed", detail: initJson }, 502);
  }

  const authorizationUrl = initJson?.data?.authorization_url as string | undefined;
  if (!authorizationUrl) {
    return corsJson({ error: "Paystack did not return authorization_url", detail: initJson }, 502);
  }

  const msg = `Complete your payment at Paystack. Subscription activates automatically after confirmation.`;
  return corsJson(
    {
      ok: true,
      customerMessage: msg,
      authorization_url: authorizationUrl,
      reference,
    },
    200,
  );
}

async function handlePaystackWebhook(req: Request, rawBody: string, payload: any) {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  const webhookSecret = Deno.env.get("PAYSTACK_WEBHOOK_SECRET")?.trim();

  if (!supabaseUrl) return corsJson({ error: "SUPABASE_URL missing" }, 500);
  if (!serviceKey) return corsJson({ error: "SUPABASE_SERVICE_ROLE_KEY missing" }, 500);
  if (!webhookSecret) return corsJson({ error: "PAYSTACK_WEBHOOK_SECRET missing" }, 500);

  const signatureHeader = req.headers.get("x-paystack-signature") ?? req.headers.get("X-Paystack-Signature");
  if (!signatureHeader) return corsJson({ error: "Missing Paystack signature" }, 401);

  const computed = await hmacSha512Hex(webhookSecret, rawBody);
  if (computed !== String(signatureHeader).trim().toLowerCase()) {
    return corsJson({ error: "Invalid Paystack signature" }, 401);
  }

  const event = String(payload?.event ?? "");
  if (!event) return corsJson({ ok: true }, 200);

  // We only activate for successful charges.
  if (event !== "charge.success") {
    return corsJson({ ok: true, ignored_event: event }, 200);
  }

  const reference = payload?.data?.reference as string | undefined;
  const amount = payload?.data?.amount as number | undefined;
  if (!reference) return corsJson({ ok: true }, 200);

  const admin = createClient(supabaseUrl, serviceKey);

  const { data: tx, error: txErr } = await admin
    .from("paystack_transactions")
    .select("id, user_id, plan_id, amount_kobo, status")
    .eq("reference", reference)
    .maybeSingle();

  if (txErr || !tx) {
    // If we don't know this reference, still return 200 so Paystack won't keep retrying forever.
    console.error("paystack webhook: transaction row not found", reference, txErr);
    return corsJson({ ok: true }, 200);
  }

  if (tx.status === "success") return corsJson({ ok: true }, 200);

  if (typeof amount === "number" && Number.isFinite(amount) && amount !== tx.amount_kobo) {
    // Mismatch: don't activate.
    console.error("paystack webhook amount mismatch", { reference, amount, expected: tx.amount_kobo });
    await admin.from("paystack_transactions").update({ status: "failed", raw_webhook: payload }).eq("id", tx.id);
    return corsJson({ ok: true }, 200);
  }

  await admin.from("paystack_transactions").update({ status: "success", raw_webhook: payload }).eq("id", tx.id);

  const { data: plan, error: planErr } = await admin
    .from("subscription_plans")
    .select("duration_type, duration_value, connects_limit_enabled, connects_per_period")
    .eq("id", tx.plan_id)
    .maybeSingle();

  if (planErr || !plan) {
    console.error("paystack webhook: plan missing", tx.plan_id, planErr);
    return corsJson({ ok: true }, 200);
  }

  const started = new Date();
  let expires = started;
  const dtype = String(plan.duration_type || "month").toLowerCase();
  const dval = Math.max(1, Number(plan.duration_value) || 1);
  if (dtype === "year") expires = addYears(started, dval);
  else expires = addMonths(started, dval);

  // Expire any previously active subscription for this user.
  await admin
    .from("user_plan_subscriptions")
    .update({ status: "expired", updated_at: new Date().toISOString() })
    .eq("user_id", tx.user_id)
    .eq("status", "active");

  const insertSub: Record<string, unknown> = {
    user_id: tx.user_id,
    plan_id: tx.plan_id,
    status: "active",
    started_at: fmtDate(started),
    expires_at: fmtDate(expires),
    payment_reference: reference,
    notes: "Paystack",
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
  if (subErr) console.error("paystack webhook: subscription insert failed", subErr);

  return corsJson({ ok: true }, 200);
}

Deno.serve(async (req) => {
  try {
    const url = new URL(req.url);

    // Redirect/callback page shown to the customer after Paystack redirects.
    if (req.method === "GET" && url.searchParams.get("redirect") === "1") {
      const reference = url.searchParams.get("reference") || "";
      return htmlPage(
        "Payment received",
        reference
          ? `Payment completed. Reference: ${reference}. You can return to the ConnectHer app now.`
          : "Payment completed. You can return to the ConnectHer app now.",
      );
    }

    // We need raw body for signature verification (webhook).
    if (req.method === "POST") {
      const rawBody = await req.text();
      let parsed: any = null;
      try {
        parsed = rawBody ? JSON.parse(rawBody) : null;
      } catch {
        parsed = null;
      }

      if (isPaystackWebhookEvent(parsed)) {
        return handlePaystackWebhook(req, rawBody, parsed);
      }

      if (parsed && typeof parsed === "object") {
        return handleInitialize(req, parsed);
      }
    }

    return corsJson({ error: "Not found" }, 404);
  } catch (e) {
    console.error("paystack-express unhandled error", e);
    return corsJson(
      {
        error: "Unhandled server error",
        detail: e instanceof Error ? (e.stack || e.message) : String(e),
      },
      500,
    );
  }
});

