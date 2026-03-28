import { createClient } from "npm:@supabase/supabase-js@2.49.1";
import { clerkBearerSource, corsJson, getClerkSubFromJwt } from "./helpers.ts";
import { resolvePaystackChannels } from "./paystack_channels.ts";

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

  const channels = resolvePaystackChannels();
  const initBody: Record<string, unknown> = {
    email,
    amount: amountKobo,
    reference,
    callback_url: redirectCallbackUrl,
    metadata: JSON.stringify({ plan_id: planId, user_id: userRow.id, plan_name: plan.name ?? null }),
    channels,
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
  const accessCode = initJson?.data?.access_code as string | undefined;
  if (!authorizationUrl) {
    return corsJson({ error: "Paystack did not return authorization_url", detail: initJson }, 502);
  }
  if (!accessCode) {
    return corsJson({ error: "Paystack did not return access_code", detail: initJson }, 502);
  }

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

