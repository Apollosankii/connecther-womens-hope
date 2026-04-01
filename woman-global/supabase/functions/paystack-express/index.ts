import { corsHeaders, corsJson, htmlPage, isPaystackWebhookEvent } from "./helpers.ts";
import { handleInitialize } from "./initialize.ts";
import { handlePaystackWebhook } from "./webhook.ts";
import { handleVerify } from "./verify.ts";

Deno.serve(async (req) => {
  try {
    if (req.method === "OPTIONS") {
      return new Response("ok", { headers: corsHeaders });
    }

    const url = new URL(req.url);

    // Redirect/callback page shown to the customer after Paystack redirects.
    if (req.method === "GET" && url.searchParams.get("redirect") === "1") {
      const reference = url.searchParams.get("reference") || "";
      return htmlPage(
        "Payment received",
        reference
          ? `Payment completed. Reference: ${reference}. You can return to the ConnectHer app now.`
          : "Payment completed. You can return to the ConnectHer app now.",
      );
    }

    // We need raw body for signature verification (webhook).
    if (req.method === "POST") {
      const rawBody = await req.text();
      let parsed: any = null;
      try {
        parsed = rawBody ? JSON.parse(rawBody) : null;
      } catch {
        parsed = null;
      }

      if (isPaystackWebhookEvent(parsed)) {
        return handlePaystackWebhook(req, rawBody, parsed);
      }

      if (parsed && typeof parsed === "object" && (parsed as Record<string, unknown>).action === "verify") {
        return handleVerify(req, parsed as Record<string, unknown>);
      }

      if (parsed && typeof parsed === "object") {
        return handleInitialize(req, parsed);
      }
    }

    return corsJson({ error: "Not found" }, 404);
  } catch (e) {
    console.error("paystack-express unhandled error", e);
    return corsJson(
      {
        error: "Unhandled server error",
        detail: e instanceof Error ? (e.stack || e.message) : String(e),
      },
      500,
    );
  }
});
