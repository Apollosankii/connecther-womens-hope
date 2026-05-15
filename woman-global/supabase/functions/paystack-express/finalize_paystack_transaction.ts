import { activatePlanSubscription } from "./_shared/activate_plan_subscription.ts";
import { restPatch, restSelectFirst } from "./supabase_rest.ts";

type TxRow = {
  id: string | number;
  user_id: string | number;
  plan_id: number;
  amount_kobo: number;
  status: string;
};

/**
 * Idempotent: completes a pending Paystack transaction row, activates subscription.
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

  const result = await activatePlanSubscription(
    supabaseUrl,
    serviceKey,
    tx.user_id,
    tx.plan_id,
    reference,
    { notes: "Paystack", rawPayload: options.rawPayload },
  );

  if (!result.ok) {
    return { ok: false, detail: result.detail };
  }

  return { ok: true, detail: result.detail === "already_activated" ? "already_completed" : "activated" };
}
