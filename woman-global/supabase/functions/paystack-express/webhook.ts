import { corsJson, hmacSha512Hex } from "./helpers.ts";
import { finalizePaystackTransaction } from "./finalize_paystack_transaction.ts";

export async function handlePaystackWebhook(req: Request, rawBody: string, payload: any) {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  const webhookSecret = (Deno.env.get("PAYSTACK_WEBHOOK_SECRET") ?? Deno.env.get("PAYSTACK_SECRET_KEY"))
    ?.trim();

  if (!supabaseUrl) return corsJson({ error: "SUPABASE_URL missing" }, 500);
  if (!serviceKey) return corsJson({ error: "SUPABASE_SERVICE_ROLE_KEY missing" }, 500);
  if (!webhookSecret) {
    return corsJson({
      error: "Webhook signing key missing",
      detail: "Set PAYSTACK_SECRET_KEY (sk_...) or optionally PAYSTACK_WEBHOOK_SECRET to the same value.",
    }, 500);
  }

  const signatureHeader = req.headers.get("x-paystack-signature") ?? req.headers.get("X-Paystack-Signature");
  if (!signatureHeader) return corsJson({ error: "Missing Paystack signature" }, 401);

  const computed = await hmacSha512Hex(webhookSecret, rawBody);
  if (computed !== String(signatureHeader).trim().toLowerCase()) {
    return corsJson({ error: "Invalid Paystack signature" }, 401);
  }

  const event = String(payload?.event ?? "");
  if (!event) return corsJson({ ok: true }, 200);

  if (event !== "charge.success") {
    return corsJson({ ok: true, ignored_event: event }, 200);
  }

  const reference = payload?.data?.reference as string | undefined;
  const amountRaw = payload?.data?.amount as number | undefined;
  if (!reference) return corsJson({ ok: true }, 200);

  const amountKobo =
    typeof amountRaw === "number" && Number.isFinite(amountRaw) ? amountRaw : null;

  const result = await finalizePaystackTransaction(supabaseUrl, serviceKey, reference, {
    amountKobo,
    rawPayload: payload,
  });

  if (!result.ok) {
    console.error("paystack webhook: finalize", reference, result.detail);
  }

  return corsJson({ ok: true }, 200);
}
