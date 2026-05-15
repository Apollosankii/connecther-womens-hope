import { activatePlanSubscription } from "./_shared/activate_plan_subscription.ts";
import { restInsert, restPatch, restSelectFirst } from "./_shared/supabase_rest.ts";
import { corsHeaders, corsJson, verifyRevenueCatAuth } from "./helpers.ts";

type RcEvent = {
  type?: string;
  id?: string;
  app_user_id?: string;
  product_id?: string;
  transaction_id?: string;
  original_transaction_id?: string;
};

const ACTIVATE_EVENTS = new Set([
  "INITIAL_PURCHASE",
  "RENEWAL",
  "PRODUCT_CHANGE",
  "UNCANCELLATION",
]);

async function resolvePlanIdByAppleProduct(
  supabaseUrl: string,
  serviceKey: string,
  productId: string,
): Promise<number | null> {
  const filter = `apple_product_id=eq.${encodeURIComponent(productId)}`;
  const { row } = await restSelectFirst<{ id: number }>(
    supabaseUrl,
    serviceKey,
    "subscription_plans",
    filter,
    "id",
  );
  return row?.id ?? null;
}

async function upsertStoreTransaction(
  supabaseUrl: string,
  serviceKey: string,
  params: {
    userId: number;
    planId: number;
    storeTransactionId: string;
    originalTransactionId?: string | null;
    revenuecatEventId?: string | null;
    status: string;
    rawPayload: unknown;
  },
): Promise<{ ok: boolean; detail: string }> {
  const txFilter =
    `platform=eq.ios&store_transaction_id=eq.${encodeURIComponent(params.storeTransactionId)}`;
  const { row: existing } = await restSelectFirst<{ id: string; status: string }>(
    supabaseUrl,
    serviceKey,
    "store_transactions",
    txFilter,
    "id,status",
  );

  if (existing?.status === "success") {
    return { ok: true, detail: "already_processed" };
  }

  if (existing?.id) {
    await restPatch(supabaseUrl, serviceKey, "store_transactions", `id=eq.${existing.id}`, {
      status: params.status,
      revenuecat_event_id: params.revenuecatEventId ?? null,
      raw_payload: params.rawPayload,
      updated_at: new Date().toISOString(),
    });
    return { ok: true, detail: "updated" };
  }

  const { error } = await restInsert(supabaseUrl, serviceKey, "store_transactions", {
    user_id: params.userId,
    plan_id: params.planId,
    platform: "ios",
    store_transaction_id: params.storeTransactionId,
    original_transaction_id: params.originalTransactionId ?? null,
    revenuecat_event_id: params.revenuecatEventId ?? null,
    status: params.status,
    raw_payload: params.rawPayload,
  });
  if (error) {
    if (error.includes("duplicate") || error.includes("23505")) {
      return { ok: true, detail: "duplicate_ignored" };
    }
    return { ok: false, detail: error };
  }
  return { ok: true, detail: "inserted" };
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return corsJson({ error: "Method not allowed" }, 405);
  }

  if (!verifyRevenueCatAuth(req)) {
    return corsJson({ error: "Unauthorized" }, 401);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!supabaseUrl || !serviceKey) {
    return corsJson({ error: "Server configuration missing" }, 500);
  }

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return corsJson({ error: "Invalid JSON" }, 400);
  }

  const event = (body.event ?? body) as RcEvent;
  const eventType = String(event.type ?? "").toUpperCase();
  const appUserId = String(event.app_user_id ?? "").trim();
  const productId = String(event.product_id ?? "").trim();
  const transactionId = String(event.transaction_id ?? event.id ?? "").trim();

  if (!appUserId || !productId || !transactionId) {
    return corsJson({ ok: true, detail: "ignored_incomplete_event" });
  }

  const userId = parseInt(appUserId, 10);
  if (!Number.isFinite(userId) || userId < 1) {
    console.error("revenuecat-webhook: invalid app_user_id", appUserId);
    return corsJson({ ok: false, error: "invalid_app_user_id" }, 400);
  }

  const planId = await resolvePlanIdByAppleProduct(supabaseUrl, serviceKey, productId);
  if (!planId) {
    console.error("revenuecat-webhook: unknown product_id", productId);
    return corsJson({ ok: false, error: "unknown_product_id", product_id: productId }, 404);
  }

  if (!ACTIVATE_EVENTS.has(eventType)) {
    if (eventType === "CANCELLATION" || eventType === "EXPIRATION") {
      return corsJson({ ok: true, detail: "acknowledged_no_activation", event_type: eventType });
    }
    return corsJson({ ok: true, detail: "ignored_event_type", event_type: eventType });
  }

  const paymentReference = `apple:${transactionId}`;
  const storeTx = await upsertStoreTransaction(supabaseUrl, serviceKey, {
    userId,
    planId,
    storeTransactionId: transactionId,
    originalTransactionId: event.original_transaction_id ?? null,
    revenuecatEventId: event.id ?? null,
    status: "pending",
    rawPayload: body,
  });

  if (!storeTx.ok && storeTx.detail !== "already_processed") {
    return corsJson({ ok: false, detail: storeTx.detail }, 500);
  }

  if (storeTx.detail === "already_processed") {
    return corsJson({ ok: true, activated: true, detail: "already_processed" });
  }

  const activation = await activatePlanSubscription(
    supabaseUrl,
    serviceKey,
    userId,
    planId,
    paymentReference,
    { notes: "Apple", rawPayload: body },
  );

  if (!activation.ok) {
    await restPatch(
      supabaseUrl,
      serviceKey,
      "store_transactions",
      `platform=eq.ios&store_transaction_id=eq.${encodeURIComponent(transactionId)}`,
      { status: "failed", updated_at: new Date().toISOString() },
    );
    return corsJson({ ok: false, activated: false, detail: activation.detail }, 500);
  }

  await restPatch(
    supabaseUrl,
    serviceKey,
    "store_transactions",
    `platform=eq.ios&store_transaction_id=eq.${encodeURIComponent(transactionId)}`,
    { status: "success", updated_at: new Date().toISOString() },
  );

  return corsJson({
    ok: true,
    activated: true,
    detail: activation.detail,
    plan_id: planId,
    connects_granted: activation.connectsGranted ?? null,
  });
});
