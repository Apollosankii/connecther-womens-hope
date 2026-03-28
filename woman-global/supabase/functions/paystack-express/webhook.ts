import { createClient } from "npm:@supabase/supabase-js@2.49.1";
import { addMonths, addYears, corsJson, fmtDate, hmacSha512Hex } from "./helpers.ts";

export async function handlePaystackWebhook(req: Request, rawBody: string, payload: any) {
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

