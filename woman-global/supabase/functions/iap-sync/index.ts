import { activatePlanSubscription } from "./_shared/activate_plan_subscription.ts";
import { restInsert, restPatch, restSelectFirst } from "./_shared/supabase_rest.ts";
import { clerkBearerSource, corsHeaders, corsJson, getClerkSubFromJwt } from "./helpers.ts";

type UserRow = { id: string | number };

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return corsJson({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!supabaseUrl || !serviceKey) {
    return corsJson({ error: "Server configuration missing" }, 500);
  }

  const clerkSub = getClerkSubFromJwt(clerkBearerSource(req));
  if (!clerkSub) {
    return corsJson({ code: "AUTH_JWT_REQUIRED", error: "Missing or invalid Authorization Bearer token" }, 401);
  }

  let parsed: Record<string, unknown>;
  try {
    parsed = await req.json();
  } catch {
    return corsJson({ error: "Invalid JSON" }, 400);
  }

  const rawPlanId = parsed.plan_id;
  const planId = typeof rawPlanId === "number" ? rawPlanId : parseInt(String(rawPlanId ?? ""), 10);
  if (!planId || planId < 1) return corsJson({ error: "plan_id required" }, 400);

  const transactionId = typeof parsed.transaction_id === "string"
    ? parsed.transaction_id.trim()
    : typeof parsed.latest_transaction_id === "string"
    ? parsed.latest_transaction_id.trim()
    : `iap-sync-${crypto.randomUUID()}`;

  const userFilter = `clerk_user_id=eq.${encodeURIComponent(clerkSub)}`;
  const { row: user, error: userErr } = await restSelectFirst<UserRow>(
    supabaseUrl,
    serviceKey,
    "users",
    userFilter,
    "id",
  );

  if (userErr || !user?.id) {
    return corsJson({ error: "User not found for this session" }, 404);
  }

  const paymentReference = `apple:${transactionId}`;
  const dupSubFilter =
    `user_id=eq.${encodeURIComponent(String(user.id))}&payment_reference=eq.${encodeURIComponent(paymentReference)}&status=eq.active`;
  const { row: activeSub } = await restSelectFirst<{ id: unknown }>(
    supabaseUrl,
    serviceKey,
    "user_plan_subscriptions",
    dupSubFilter,
    "id",
  );

  if (activeSub?.id) {
    return corsJson({ ok: true, activated: true, detail: "already_active", plan_id: planId });
  }

  const txFilter = `platform=eq.ios&store_transaction_id=eq.${encodeURIComponent(transactionId)}`;
  const { row: existingTx } = await restSelectFirst<{ status: string }>(
    supabaseUrl,
    serviceKey,
    "store_transactions",
    txFilter,
    "status",
  );

  if (!existingTx) {
    await restInsert(supabaseUrl, serviceKey, "store_transactions", {
      user_id: user.id,
      plan_id: planId,
      platform: "ios",
      store_transaction_id: transactionId,
      status: "pending",
      raw_payload: parsed,
    });
  }

  const activation = await activatePlanSubscription(
    supabaseUrl,
    serviceKey,
    user.id,
    planId,
    paymentReference,
    { notes: "Apple", rawPayload: parsed },
  );

  if (!activation.ok) {
    return corsJson({ ok: false, activated: false, detail: activation.detail }, 500);
  }

  await restPatch(supabaseUrl, serviceKey, "store_transactions", txFilter, {
    status: "success",
    updated_at: new Date().toISOString(),
  });

  return corsJson({
    ok: true,
    activated: true,
    detail: activation.detail,
    plan_id: planId,
    connects_granted: activation.connectsGranted ?? null,
    transaction_id: transactionId,
  });
});
