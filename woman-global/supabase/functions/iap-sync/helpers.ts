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
  return null;
}
