import { clerkBearerSource, corsJson, getClerkSubFromJwt } from "./helpers.ts";
import { resolvePaystackChannels } from "./paystack_channels.ts";
import { restInsert, restSelectFirst } from "./supabase_rest.ts";

const PLAN_COLUMNS =
  "id,price,currency,is_active,name,duration_type,duration_value,connects_limit_enabled,connects_per_period";

type UserRow = { id: string | number };
type PlanRow = {
  id: unknown;
  price: unknown;
  currency: unknown;
  is_active: unknown;
  name: unknown;
  duration_type: unknown;
  duration_value: unknown;
  connects_limit_enabled: unknown;
  connects_per_period: unknown;
};

export async function handleInitialize(req: Request, parsed: Record<string, unknown>) {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  const paystackSecretKey = Deno.env.get("PAYSTACK_SECRET_KEY")?.trim();

  if (!supabaseUrl) return corsJson({ error: "SUPABASE_URL missing" }, 500);
  if (!serviceKey) return corsJson({ error: "SUPABASE_SERVICE_ROLE_KEY missing" }, 500);
  if (!paystackSecretKey) return corsJson({ error: "PAYSTACK_SECRET_KEY missing" }, 500);

  const clerkSub = getClerkSubFromJwt(clerkBearerSource(req));
  if (!clerkSub) {
    return corsJson({ code: "AUTH_JWT_REQUIRED", error: "Missing or invalid Authorization Bearer token" }, 401);
  }

  const rawPlanId = parsed.plan_id;
  const planId = typeof rawPlanId === "number" ? rawPlanId : parseInt(String(rawPlanId ?? ""), 10);
  if (!planId || planId < 1) return corsJson({ error: "plan_id required" }, 400);

  const emailRaw = parsed.email;
  const email = typeof emailRaw === "string" ? emailRaw.trim() : "";
  if (!email) return corsJson({ error: "email required" }, 400);

  const userFilter = `clerk_user_id=eq.${encodeURIComponent(clerkSub)}`;
  const planFilter = `id=eq.${planId}`;

  const [userLookup, planLookup] = await Promise.all([
    restSelectFirst<UserRow>(supabaseUrl, serviceKey, "users", userFilter, "id"),
    restSelectFirst<PlanRow>(supabaseUrl, serviceKey, "subscription_plans", planFilter, PLAN_COLUMNS),
  ]);

  if (userLookup.error) {
    return corsJson({ error: "Failed to load user", detail: userLookup.error }, 500);
  }
  if (!userLookup.row?.id) {
    return corsJson({ error: "User not found for this session" }, 404);
  }

  if (planLookup.error) {
    return corsJson({ error: "Failed to load plan", detail: planLookup.error }, 500);
  }
  const plan = planLookup.row;
  if (!plan || plan.is_active !== true) {
    return corsJson({ error: "Plan not found or inactive" }, 404);
  }

  const priceNum = Number(plan.price);
  if (!Number.isFinite(priceNum) || priceNum <= 0) return corsJson({ error: "Invalid plan price" }, 400);

  /** Smallest currency unit (kobo, cents, etc.) — must match [currency] sent to Paystack. */
  const amountMinor = Math.round(priceNum * 100);
  if (amountMinor < 1) return corsJson({ error: "Invalid amount" }, 400);

  const currency = String(plan.currency || "KES").toUpperCase();

  const userRow = userLookup.row;
  const reference = `PAY-${String(userRow.id).slice(0, 8)}-${planId}-${crypto.randomUUID()}`.slice(0, 36);
  const fnSlug = (Deno.env.get("PAYSTACK_EDGE_SLUG")?.trim() || "paystack-checkout").replace(/^\/+|\/+$/g, "");
  const redirectCallbackUrl = `${supabaseUrl}/functions/v1/${fnSlug}?redirect=1`;

  const { error: txErr } = await restInsert(supabaseUrl, serviceKey, "paystack_transactions", {
    user_id: userRow.id,
    plan_id: planId,
    amount_kobo: amountMinor,
    currency,
    email,
    reference,
    status: "pending",
  });
  if (txErr) return corsJson({ error: "Failed to create payment record", detail: txErr }, 500);

  const paystackBase = (Deno.env.get("PAYSTACK_BASE_URL")?.trim() || "https://api.paystack.co").replace(/\/$/, "");
  const initUrl = `${paystackBase}/transaction/initialize`;

  const channels = resolvePaystackChannels();
  const initBody: Record<string, unknown> = {
    email,
    amount: amountMinor,
    currency,
    reference,
    callback_url: redirectCallbackUrl,
    metadata: { plan_id: planId, user_id: userRow.id, plan_name: plan.name ?? null },
    channels,
  };

  let initResp: Response;
  const paystackAbort = new AbortController();
  const paystackTimer = setTimeout(() => paystackAbort.abort(), 25_000);
  try {
    initResp = await fetch(initUrl, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${paystackSecretKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(initBody),
      signal: paystackAbort.signal,
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.error("paystack initialize fetch failed", msg);
    return corsJson({ error: "Paystack request failed or timed out", detail: msg }, 504);
  } finally {
    clearTimeout(paystackTimer);
  }

  const initJson = (await initResp.json().catch(() => ({}))) as any;
  if (!initResp.ok) {
    console.error("paystack initialize failed", initResp.status, initJson);
    return corsJson({ error: "Paystack initialize failed", detail: initJson }, 502);
  }

  const authorizationUrl = initJson?.data?.authorization_url as string | undefined;
  const accessCode = initJson?.data?.access_code as string | undefined;
  if (!authorizationUrl) {
    return corsJson({ error: "Paystack did not return authorization_url", detail: initJson }, 502);
  }
  if (!accessCode) {
    return corsJson({ error: "Paystack did not return access_code", detail: initJson }, 502);
  }

  const paystackMode = paystackSecretKey.startsWith("sk_live")
    ? "live"
    : paystackSecretKey.startsWith("sk_test")
    ? "test"
    : "unknown";
  console.log("paystack initialize ok", { paystackMode, reference, accessCodeLength: accessCode.length });

  const msg = `Complete your payment at Paystack. Subscription activates automatically after confirmation.`;
  return corsJson(
    {
      ok: true,
      customerMessage: msg,
      access_code: accessCode,
      authorization_url: authorizationUrl,
      reference,
    },
    200,
  );
}
