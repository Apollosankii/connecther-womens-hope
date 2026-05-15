export const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

export function corsJson(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

export function verifyRevenueCatAuth(req: Request): boolean {
  const secret = Deno.env.get("REVENUECAT_WEBHOOK_SECRET")?.trim();
  if (!secret) return true;
  const auth = req.headers.get("Authorization")?.trim() ?? "";
  if (auth === `Bearer ${secret}` || auth === secret) return true;
  const header = req.headers.get("X-RevenueCat-Webhook-Secret")?.trim();
  return header === secret;
}
