import { clerkBearerSource, corsJson, getClerkSubFromJwt } from "./helpers.ts";
import { finalizePaystackTransaction } from "./finalize_paystack_transaction.ts";
import { restSelectFirst } from "./supabase_rest.ts";

type TxRow = {
  id: string | number;
  user_id: string | number;
  plan_id: number;
  amount_kobo: number;
  status: string;
};

type UserRow = { id: string | number };

/** Client calls this immediately after PaymentSheet success so activation is not webhook-only. */
export async function handleVerify(req: Request, parsed: Record<string, unknown>) {
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

  const rawRef = parsed.reference;
  const reference = typeof rawRef === "string" ? rawRef.trim() : "";
  if (!reference) return corsJson({ error: "reference required" }, 400);

  const refFilter = `reference=eq.${encodeURIComponent(reference)}`;
  const { row: tx, error: txErr } = await restSelectFirst<TxRow>(
    supabaseUrl,
    serviceKey,
    "paystack_transactions",
    refFilter,
    "id,user_id,plan_id,amount_kobo,status",
  );

  if (txErr) {
    return corsJson({ error: "Failed to load transaction", detail: txErr }, 500);
  }
  if (!tx) {
    return corsJson({ error: "Transaction not found", reference }, 404);
  }

  const userFilter = `clerk_user_id=eq.${encodeURIComponent(clerkSub)}`;
  const { row: user, error: userErr } = await restSelectFirst<UserRow>(
    supabaseUrl,
    serviceKey,
    "users",
    userFilter,
    "id",
  );

  if (userErr || !user) {
    return corsJson({ error: "User not found for this session" }, 404);
  }
  if (String(user.id) !== String(tx.user_id)) {
    return corsJson({ error: "This payment does not belong to your account" }, 403);
  }

  const paystackBase = (Deno.env.get("PAYSTACK_BASE_URL")?.trim() || "https://api.paystack.co").replace(/\/$/, "");
  const verifyUrl = `${paystackBase}/transaction/verify/${encodeURIComponent(reference)}`;

  let verifyResp: Response;
  try {
    verifyResp = await fetch(verifyUrl, {
      method: "GET",
      headers: { Authorization: `Bearer ${paystackSecretKey}` },
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.error("paystack verify fetch failed", msg);
    return corsJson({ error: "Paystack verify request failed", detail: msg }, 504);
  }

  const verifyJson = (await verifyResp.json().catch(() => ({}))) as Record<string, unknown>;
  if (!verifyResp.ok) {
    console.error("paystack verify HTTP error", verifyResp.status, verifyJson);
    return corsJson(
      { error: "Paystack verify failed", status: verifyResp.status, detail: verifyJson },
      502,
    );
  }

  if (verifyJson.status !== true) {
    return corsJson({
      ok: false,
      activated: false,
      message: verifyJson.message ?? "Paystack verify unsuccessful",
      detail: verifyJson,
    }, 200);
  }

  const data = verifyJson.data as Record<string, unknown> | undefined;
  const payStatus = String(data?.status ?? "");
  if (payStatus !== "success") {
    return corsJson({
      ok: false,
      activated: false,
      paystack_status: payStatus,
      gateway_response: data?.gateway_response,
    }, 200);
  }

  const amount = data?.amount as number | undefined;
  const finalResult = await finalizePaystackTransaction(supabaseUrl, serviceKey, reference, {
    amountKobo: typeof amount === "number" && Number.isFinite(amount) ? amount : null,
    rawPayload: verifyJson,
  });

  if (!finalResult.ok) {
    return corsJson(
      {
        ok: false,
        activated: false,
        detail: finalResult.detail,
      },
      200,
    );
  }

  return corsJson({
    ok: true,
    activated: true,
    detail: finalResult.detail,
    plan_id: tx.plan_id,
    reference,
  }, 200);
}
