import { addMonths, addYears, fmtDate } from "./date_helpers.ts";
import { restInsert, restPatch, restSelectFirst } from "./supabase_rest.ts";

export type PlanRow = {
  duration_type: unknown;
  duration_value: unknown;
  connects_limit_enabled: unknown;
  connects_per_period: unknown;
};

export type ActivatePlanOptions = {
  notes?: string;
  rawPayload?: unknown;
};

/**
 * Expire prior active subscription and insert a new active row with connects.
 * Idempotent when paymentReference already activated (checks user_plan_subscriptions).
 */
export async function activatePlanSubscription(
  supabaseUrl: string,
  serviceKey: string,
  userId: string | number,
  planId: number,
  paymentReference: string,
  options: ActivatePlanOptions = {},
): Promise<{ ok: boolean; detail: string; connectsGranted?: number | null }> {
  const ref = paymentReference.trim();
  if (!ref) return { ok: false, detail: "payment_reference_required" };

  const dupFilter =
    `user_id=eq.${encodeURIComponent(String(userId))}&payment_reference=eq.${encodeURIComponent(ref)}&status=eq.active`;
  const { row: existing } = await restSelectFirst<{ id: unknown }>(
    supabaseUrl,
    serviceKey,
    "user_plan_subscriptions",
    dupFilter,
    "id",
  );
  if (existing?.id) {
    return { ok: true, detail: "already_activated" };
  }

  const planFilter = `id=eq.${planId}`;
  const { row: plan, error: planErr } = await restSelectFirst<PlanRow>(
    supabaseUrl,
    serviceKey,
    "subscription_plans",
    planFilter,
    "duration_type,duration_value,connects_limit_enabled,connects_per_period",
  );

  if (planErr || !plan) {
    console.error("activate: plan missing", planId, planErr);
    return { ok: false, detail: "plan_missing" };
  }

  const started = new Date();
  let expires = started;
  const dtype = String(plan.duration_type || "month").toLowerCase();
  const dval = Math.max(1, Number(plan.duration_value) || 1);
  if (dtype === "year") expires = addYears(started, dval);
  else expires = addMonths(started, dval);

  const subUserFilter =
    `user_id=eq.${encodeURIComponent(String(userId))}&status=eq.active`;
  await restPatch(supabaseUrl, serviceKey, "user_plan_subscriptions", subUserFilter, {
    status: "expired",
    updated_at: new Date().toISOString(),
  });

  const notes = options.notes ?? "Store";
  const insertSub: Record<string, unknown> = {
    user_id: userId,
    plan_id: planId,
    status: "active",
    started_at: fmtDate(started),
    expires_at: fmtDate(expires),
    payment_reference: ref,
    notes,
  };

  let connectsGranted: number | null = null;
  const limitOn = plan.connects_limit_enabled === true;
  const perRaw = plan.connects_per_period;
  const perNum = perRaw != null ? Number(perRaw) : NaN;
  if (limitOn && Number.isFinite(perNum) && perNum >= 0) {
    connectsGranted = Math.floor(perNum);
    insertSub.connects_granted = connectsGranted;
    insertSub.connects_used = 0;
    insertSub.connects_period_started_at = fmtDate(started);
  }

  const { error: subErr } = await restInsert(supabaseUrl, serviceKey, "user_plan_subscriptions", insertSub);
  if (subErr) {
    console.error("activate: subscription insert failed", subErr);
    return { ok: false, detail: `subscription_insert_failed: ${subErr}` };
  }

  return { ok: true, detail: "activated", connectsGranted };
}
