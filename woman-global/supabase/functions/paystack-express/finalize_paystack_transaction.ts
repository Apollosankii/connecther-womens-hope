import { addMonths, addYears, fmtDate } from "./helpers.ts";
import { restInsert, restPatch, restSelectFirst } from "./supabase_rest.ts";

type TxRow = {
  id: string | number;
  user_id: string | number;
  plan_id: number;
  amount_kobo: number;
  status: string;
};

type PlanRow = {
  duration_type: unknown;
  duration_value: unknown;
  connects_limit_enabled: unknown;
  connects_per_period: unknown;
};

/**
 * Idempotent: completes a pending Paystack transaction row, inserts active subscription.
 * Shared by webhook (charge.success) and client-driven verify endpoint.
 */
export async function finalizePaystackTransaction(
  supabaseUrl: string,
  serviceKey: string,
  reference: string,
  options: {
    amountKobo?: number | null;
    rawPayload?: unknown;
  } = {},
): Promise<{ ok: boolean; detail: string }> {
  const refFilter = `reference=eq.${encodeURIComponent(reference)}`;
  const { row: tx, error: txErr } = await restSelectFirst<TxRow>(
    supabaseUrl,
    serviceKey,
    "paystack_transactions",
    refFilter,
    "id,user_id,plan_id,amount_kobo,status",
  );

  if (txErr || !tx) {
    console.error("finalize: transaction row not found", reference, txErr);
    return { ok: false, detail: "transaction_not_found" };
  }

  if (tx.status === "success") {
    return { ok: true, detail: "already_completed" };
  }

  const idFilter = `id=eq.${encodeURIComponent(String(tx.id))}`;
  const amount = options.amountKobo;

  if (typeof amount === "number" && Number.isFinite(amount) && amount !== tx.amount_kobo) {
    console.error("finalize: amount mismatch", { reference, amount, expected: tx.amount_kobo });
    await restPatch(supabaseUrl, serviceKey, "paystack_transactions", idFilter, {
      status: "failed",
      ...(options.rawPayload != null ? { raw_webhook: options.rawPayload } : {}),
    });
    return { ok: false, detail: "amount_mismatch" };
  }

  await restPatch(supabaseUrl, serviceKey, "paystack_transactions", idFilter, {
    status: "success",
    ...(options.rawPayload != null ? { raw_webhook: options.rawPayload } : {}),
  });

  const planFilter = `id=eq.${tx.plan_id}`;
  const { row: plan, error: planErr } = await restSelectFirst<PlanRow>(
    supabaseUrl,
    serviceKey,
    "subscription_plans",
    planFilter,
    "duration_type,duration_value,connects_limit_enabled,connects_per_period",
  );

  if (planErr || !plan) {
    console.error("finalize: plan missing", tx.plan_id, planErr);
    return { ok: false, detail: "plan_missing" };
  }

  const started = new Date();
  let expires = started;
  const dtype = String(plan.duration_type || "month").toLowerCase();
  const dval = Math.max(1, Number(plan.duration_value) || 1);
  if (dtype === "year") expires = addYears(started, dval);
  else expires = addMonths(started, dval);

  const subUserFilter =
    `user_id=eq.${encodeURIComponent(String(tx.user_id))}&status=eq.active`;
  await restPatch(supabaseUrl, serviceKey, "user_plan_subscriptions", subUserFilter, {
    status: "expired",
    updated_at: new Date().toISOString(),
  });

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

  const { error: subErr } = await restInsert(supabaseUrl, serviceKey, "user_plan_subscriptions", insertSub);
  if (subErr) {
    console.error("finalize: subscription insert failed", subErr);
    return { ok: false, detail: `subscription_insert_failed: ${subErr}` };
  }

  return { ok: true, detail: "activated" };
}
