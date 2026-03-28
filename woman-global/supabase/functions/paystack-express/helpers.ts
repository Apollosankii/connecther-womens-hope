export const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-clerk-authorization, x-client-info, apikey, content-type",
};

export function corsJson(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

export function htmlPage(title: string, message: string) {
  return new Response(
    `<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${title}</title>
  </head>
  <body style="font-family: system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial; padding: 24px;">
    <h2 style="margin-top: 0;">${title}</h2>
    <p>${message}</p>
    <p style="color:#666; font-size: 13px;">You can close this tab and return to the ConnectHer app. Your subscription will activate automatically after Paystack confirmation.</p>
  </body>
</html>`,
    {
      headers: { "Content-Type": "text/html; charset=utf-8", ...corsHeaders },
    },
  );
}

// Decode JWT payload; UTF-8-safe middle-segment parse (same approach as mpesa-express).
export function jwtPayload(jwt: string): Record<string, unknown> | null {
  const parts = jwt.split(".");
  if (parts.length < 2) return null;
  try {
    const pad = parts[1].length % 4 === 0 ? "" : "=".repeat(4 - (parts[1].length % 4));
    const b64 = parts[1].replace(/-/g, "+").replace(/_/g, "/") + pad;
    const binary = atob(b64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    const text = new TextDecoder().decode(bytes);
    return JSON.parse(text) as Record<string, unknown>;
  } catch {
    return null;
  }
}

export function getClerkSubFromJwt(authHeader: string | null): string | null {
  const m = authHeader?.match(/^Bearer\s+(.+)$/i);
  if (!m) return null;
  const token = m[1].trim();
  const payload = jwtPayload(token);
  return payload && typeof payload.sub === "string" ? payload.sub : null;
}

export function clerkBearerSource(req: Request): string | null {
  const a = req.headers.get("Authorization") ?? req.headers.get("authorization");
  if (a?.match(/^Bearer\s+/i)) return a;
  const x = req.headers.get("X-Clerk-Authorization") ?? req.headers.get("x-clerk-authorization");
  if (x?.match(/^Bearer\s+/i)) return x;
  return null;
}

export function fmtDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

export function addMonths(d: Date, n: number): Date {
  const x = new Date(d);
  x.setMonth(x.getMonth() + n);
  return x;
}

export function addYears(d: Date, n: number): Date {
  const x = new Date(d);
  x.setFullYear(x.getFullYear() + n);
  return x;
}

export async function hmacSha512Hex(secret: string, data: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-512" },
    false,
    ["sign"],
  );

  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(data));
  const bytes = new Uint8Array(sig);
  // hex lowercase (matches Paystack docs)
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

export function isPaystackWebhookEvent(payload: unknown): payload is {
  event: string;
  data: Record<string, unknown>;
} {
  return !!payload && typeof payload === "object" && "event" in payload && typeof (payload as any).event === "string";
}

