/**
 * Figma-style multi-line quotes collapsed into one create_booking_request price.
 * Empty `lines` → appendQuoteBreakdown returns the trimmed user message (or '').
 */
export type QuoteLine = { label: string; unitPrice: number; quantity: number };

export function quoteTotal(lines: readonly QuoteLine[]): number {
  return lines.reduce((sum, l) => sum + l.unitPrice * l.quantity, 0);
}

export function quoteBreakdown(lines: readonly QuoteLine[]): string {
  return lines
    .map((l) => {
      const sub = l.unitPrice * l.quantity;
      return `${l.label} × ${l.quantity} @ ${l.unitPrice} = ${sub}`;
    })
    .join('\n');
}

/** Compose user note + optional quote block for RPC `message`. */
export function appendQuoteBreakdown(userMessage: string, lines: readonly QuoteLine[]): string {
  const base = userMessage.trim();
  if (lines.length === 0) return base;
  const quoteBlock = `Quote:\n${quoteBreakdown(lines)}`;
  if (!base) return quoteBlock;
  return `${base}\n\n${quoteBlock}`;
}
