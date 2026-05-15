/** E.164 for Kenya-style entry (0…, 254…, +254…, or 9 digits). Mirrors Android `ConnectHerPhoneAuth.normalizeKenyaE164`. */
export function normalizeKenyaE164(raw: string): string | null {
  const s = raw.trim().replace(/\s/g, '').replace(/-/g, '');
  if (!s) return null;
  if (s.startsWith('+254') && s.length >= 12) return s;
  if (s.startsWith('254') && s.length >= 11) return `+${s}`;
  if (s.startsWith('0') && s.length >= 9) return `+254${s.slice(1)}`;
  if (s.length === 9 && /^\d+$/.test(s)) return `+254${s}`;
  if (s.startsWith('+') && s.length >= 10) return s;
  return null;
}
