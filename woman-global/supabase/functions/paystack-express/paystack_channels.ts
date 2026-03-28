/**
 * Paystack Initialize Transaction `channels` allow-list.
 * @see https://docs-v2.paystack.com/payments/payment-channels/
 */
const ALLOWED = new Set([
  "card",
  "bank",
  "ussd",
  "qr",
  "mobile_money",
  "bank_transfer",
]);

const DEFAULT_CHANNELS = Array.from(ALLOWED);

function parseChannelsRaw(raw: string): string[] {
  const t = raw.trim();
  if (!t) return [];
  if (t.startsWith("[")) {
    try {
      const arr = JSON.parse(t);
      if (!Array.isArray(arr)) return [];
      return arr.map((x) => String(x).trim().toLowerCase()).filter(Boolean);
    } catch {
      return [];
    }
  }
  return t.split(/[\s,]+/).map((s) => s.trim().toLowerCase()).filter(Boolean);
}

/**
 * Returns validated channel names for `transaction/initialize`.
 * Env `PAYSTACK_CHANNELS`: comma-separated or JSON array, e.g. `card,ussd,mobile_money` or `["card","bank"]`.
 * If unset, uses all allowed channels (Paystack still only shows what's enabled for your account/currency).
 */
export function resolvePaystackChannels(): string[] {
  const raw = Deno.env.get("PAYSTACK_CHANNELS")?.trim();
  if (!raw) return [...DEFAULT_CHANNELS];
  const parsed = parseChannelsRaw(raw);
  const filtered = parsed.filter((c) => ALLOWED.has(c));
  if (filtered.length === 0) {
    console.warn("paystack: PAYSTACK_CHANNELS parsed empty after validation; using defaults");
    return [...DEFAULT_CHANNELS];
  }
  return [...new Set(filtered)];
}
