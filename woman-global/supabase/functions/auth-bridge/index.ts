import { createClient } from "npm:@supabase/supabase-js@2.49.1";
import { createRemoteJWKSet, jwtVerify, SignJWT } from "npm:jose@5.9.6";

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-clerk-authorization, x-client-info, apikey, content-type",
};

function corsJson(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function bearerToken(req: Request): string | null {
  const a = req.headers.get("Authorization") ?? req.headers.get("authorization");
  const m = a?.match(/^Bearer\s+(.+)$/i);
  return m ? m[1].trim() : null;
}

async function sha256Base36(input: string, length = 10): Promise<string> {
  const bytes = new TextEncoder().encode(input);
  const hash = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
  // Take first 8 bytes to keep short/stable.
  let n = 0n;
  for (let i = 0; i < 8; i++) n = (n << 8n) | BigInt(hash[i]);
  return n.toString(36).padStart(length, "0").slice(0, length);
}

async function verifyFirebaseIdToken(idToken: string) {
  const projectId = Deno.env.get("FIREBASE_PROJECT_ID")?.trim();
  if (!projectId) throw new Error("FIREBASE_PROJECT_ID missing");

  const jwks = createRemoteJWKSet(
    new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
  );

  const { payload } = await jwtVerify(idToken, jwks, {
    issuer: `https://securetoken.google.com/${projectId}`,
    audience: projectId,
  });

  const uid = typeof payload.sub === "string" ? payload.sub : "";
  if (!uid) throw new Error("Firebase token missing sub");

  const email = typeof payload.email === "string" ? payload.email : null;
  const name = typeof payload.name === "string" ? payload.name : null;
  const phone = typeof payload.phone_number === "string" ? payload.phone_number : null;

  return { uid, email, name, phone, raw: payload };
}

function splitName(name: string | null): { first: string | null; last: string | null } {
  if (!name) return { first: null, last: null };
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return { first: null, last: null };
  if (parts.length === 1) return { first: parts[0], last: null };
  return { first: parts[0], last: parts.slice(1).join(" ") };
}

function normalizeEmail(email: string | null | undefined): string {
  return (email ?? "").trim().toLowerCase();
}

/** True if Postgres / PostgREST reports a unique violation (shape varies by client). */
function isUniqueViolation(err: { code?: string; message?: string; details?: string } | null): boolean {
  if (!err) return false;
  const msg = `${err.message ?? ""} ${err.details ?? ""}`.toLowerCase();
  return (
    err.code === "23505" ||
    msg.includes("users_email_lower_unique") ||
    msg.includes("duplicate key") ||
    msg.includes("unique constraint")
  );
}

function fallbackNames(email: string | null): { first: string; last: string } {
  if (!email) return { first: "ConnectHer", last: "User" };
  const local = email.split("@")[0]?.trim() || "";
  const cleaned = local.replace(/[._-]+/g, " ").trim();
  if (!cleaned) return { first: "ConnectHer", last: "User" };
  const parts = cleaned.split(/\s+/).filter(Boolean);
  if (parts.length === 1) return { first: parts[0], last: "User" };
  return { first: parts[0], last: parts.slice(1).join(" ") };
}

/**
 * HMAC secret used to mint user JWTs for PostgREST/RLS.
 * Custom Edge secrets cannot use the reserved `SUPABASE_` prefix — use one of these instead.
 */
function getJwtSigningSecret(): string {
  const a = Deno.env.get("AUTH_BRIDGE_JWT_SECRET")?.trim();
  if (a) return a;
  const b = Deno.env.get("JWT_SECRET")?.trim();
  if (b) return b;
  const legacy = Deno.env.get("SUPABASE_JWT_SECRET")?.trim();
  if (legacy) return legacy;
  throw new Error(
    "JWT signing secret missing: set Edge secret AUTH_BRIDGE_JWT_SECRET (or JWT_SECRET) to your project legacy JWT secret — names starting with SUPABASE_ are reserved",
  );
}

async function mintSupabaseJwt(params: {
  uid: string;
  email: string | null;
  role: string;
  phone?: string | null;
}) {
  const secret = getJwtSigningSecret();

  const now = Math.floor(Date.now() / 1000);
  const exp = now + 60 * 60; // 1 hour

  const key = new TextEncoder().encode(secret);

  const jwt = await new SignJWT({
    email: params.email ?? undefined,
    role: params.role,
    phone: params.phone?.trim() || undefined,
  })
    .setProtectedHeader({ alg: "HS256", typ: "JWT" })
    .setSubject(params.uid)
    .setIssuer("supabase")
    .setAudience("authenticated")
    .setIssuedAt(now)
    .setExpirationTime(exp)
    .sign(key);

  return jwt;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: { ...corsHeaders } });
  }

  if (req.method !== "POST") {
    return corsJson({ error: "Method not allowed" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.trim();
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")?.trim();
  if (!supabaseUrl) return corsJson({ error: "SUPABASE_URL missing" }, 500);
  if (!serviceKey) return corsJson({ error: "SUPABASE_SERVICE_ROLE_KEY missing" }, 500);

  const idToken = bearerToken(req);
  if (!idToken) return corsJson({ code: "AUTH_REQUIRED", error: "Missing Authorization Bearer token" }, 401);

  try {
    const verified = await verifyFirebaseIdToken(idToken);
    const { first, last } = splitName(verified.name);
    const fallback = fallbackNames(verified.email);
    const phoneFromToken = verified.phone?.trim() || null;

    const admin = createClient(supabaseUrl, serviceKey);

    const { data: existing, error: existingErr } = await admin
      .from("users")
      .select("id, user_id, email, phone, phone_verified_at")
      .eq("clerk_user_id", verified.uid)
      .maybeSingle();
    if (existingErr) {
      return corsJson({ error: "Failed to lookup user", detail: existingErr.message }, 500);
    }

    const userId = (existing?.user_id as string | null) ??
      `usr_${await sha256Base36(verified.uid, 12)}`;

    const dbPhoneRaw = (existing as { phone?: string | null } | null)?.phone;
    const dbPhone = (dbPhoneRaw ?? "").trim() || null;
    const phoneForJwt = dbPhone || phoneFromToken;

    if (existing?.id != null) {
      /**
       * Best-effort: copy Firebase email onto this row. Must never block login.
       *
       * Before `users_email_lower_unique`, duplicates were allowed and this always worked. After the
       * migration, another legacy row may still hold the same normalized email → UPDATE can hit a
       * unique violation. Supabase may not always surface Postgres code `23505` on the error object,
       * so we treat email sync as optional: log and continue.
       *
       * Also skip UPDATE when the DB already matches Firebase (avoids useless writes).
       */
      if (verified.email) {
        const row = existing as { id: number; user_id?: string | null; email?: string | null };
        if (normalizeEmail(row.email) !== normalizeEmail(verified.email)) {
          const { error: emailErr } = await admin
            .from("users")
            .update({ email: verified.email })
            .eq("clerk_user_id", verified.uid);
          if (emailErr) {
            console.warn("auth-bridge: email sync failed (login continues)", {
              uid: verified.uid,
              email: verified.email,
              err: emailErr,
            });
          }
        }
      }
      // Only sync phone from Firebase token when the token carries a verified phone number.
      // Twilio-verified phones live in Postgres only (no Firebase phone_number claim).
      if (phoneFromToken) {
        const row = existing as { id?: number; phone?: string | null };
        const rowPhone = (row.phone ?? "").trim();
        if (rowPhone !== phoneFromToken) {
          const { error: phoneErr } = await admin
            .from("users")
            .update({ phone: phoneFromToken })
            .eq("clerk_user_id", verified.uid);
          if (phoneErr) {
            console.warn("auth-bridge: phone sync failed (login continues)", {
              uid: verified.uid,
              phone: phoneFromToken,
              err: phoneErr,
            });
          }
        }
      }
    } else {
      // `clerk_user_id` is a legacy column name; value is the Firebase Auth UID (JWT `sub` from auth-bridge).
      const insertPayload: Record<string, unknown> = {
        clerk_user_id: verified.uid,
        user_id: userId,
        title: "Ms",
        first_name: first ?? fallback.first,
        last_name: last ?? fallback.last,
        phone: phoneFromToken ?? "0000000000",
        password: `firebase:${verified.uid}`,
      };
      if (verified.email) insertPayload.email = verified.email;

      const { error: insertErr } = await admin.from("users").insert(insertPayload);
      if (insertErr) {
        const dup = isUniqueViolation(insertErr);
        if (dup) {
          return corsJson(
            {
              code: "EMAIL_TAKEN",
              error: "This email is already registered. Sign in or use another email.",
              detail: insertErr.message,
            },
            409,
          );
        }
        return corsJson({ error: "Failed to create user", detail: insertErr.message }, 500);
      }
    }

    const supabaseJwt = await mintSupabaseJwt({
      uid: verified.uid,
      email: verified.email,
      role: "authenticated",
      phone: phoneForJwt,
    });

    return corsJson({
      supabase_jwt: supabaseJwt,
      user_id: userId,
      firebase_uid: verified.uid,
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    const isServerMisconfig =
      msg.includes("FIREBASE_PROJECT_ID missing") ||
      msg.includes("JWT signing secret missing") ||
      msg.includes("SUPABASE_SERVICE_ROLE_KEY missing") ||
      msg.includes("SUPABASE_URL missing");
    if (isServerMisconfig) {
      console.error("auth-bridge server misconfig:", msg);
      return corsJson({ code: "SERVER_MISCONFIG", error: "Server misconfigured", detail: msg }, 500);
    }
    return corsJson({ code: "INVALID_TOKEN", error: "Invalid token", detail: msg }, 401);
  }
});

