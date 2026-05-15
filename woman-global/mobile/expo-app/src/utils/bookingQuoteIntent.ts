/**
 * Serialize quote lines for navigation — parity with Android `BookingQuoteIntentHelper`.
 */
import type { QuoteLine } from '@/utils/bookingQuote';

export function encodeQuoteLinesForIntent(lines: readonly QuoteLine[]): string {
  const payload = lines.map((l) => ({
    label: l.label,
    unitPrice: l.unitPrice,
    quantity: l.quantity,
  }));
  return JSON.stringify(payload);
}

export function decodeQuoteLinesFromIntent(json: string | null | undefined): QuoteLine[] {
  if (json == null || !String(json).trim()) return [];
  try {
    const a = JSON.parse(String(json)) as unknown;
    if (!Array.isArray(a)) return [];
    const out: QuoteLine[] = [];
    for (const item of a) {
      if (!item || typeof item !== 'object') continue;
      const o = item as Record<string, unknown>;
      const label = String(o.label ?? '').trim();
      if (!label) continue;
      const qty = Math.max(0, Math.floor(Number(o.quantity ?? 0)) || 0);
      const unitPrice = Math.max(0, Number(o.unitPrice ?? 0) || 0);
      out.push({ label, unitPrice, quantity: qty });
    }
    return out;
  } catch {
    return [];
  }
}
