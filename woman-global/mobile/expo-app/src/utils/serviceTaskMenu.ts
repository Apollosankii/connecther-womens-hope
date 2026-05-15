/**
 * Parses `services.task_menu` JSON — parity with Android `ServiceTaskMenuParser`
 * (see `woman-global/supabase/migrations/20260513180000_services_task_menu.sql`).
 */
import type { QuoteLine } from '@/utils/bookingQuote';

export type TaskMenuSection = { type: 'section'; title: string; subtitle?: string };
export type TaskMenuQuantity = {
  type: 'quantity';
  key: string;
  title: string;
  unitLabel?: string;
  unitPrice: number;
  imageUrl?: string;
  quantity: number;
  min: number;
  max: number;
};
export type TaskMenuToggle = {
  type: 'toggle';
  key: string;
  title: string;
  unitLabel?: string;
  unitPrice: number;
  imageUrl?: string;
  checked: boolean;
};

export type TaskMenuRow = TaskMenuSection | TaskMenuQuantity | TaskMenuToggle;

export type ParsedTaskMenu = {
  bannerImageUrl?: string;
  rows: TaskMenuRow[];
};

function readJsonString(raw: unknown): string {
  if (raw == null) return '';
  if (typeof raw === 'string') return raw.trim();
  try {
    return JSON.stringify(raw);
  } catch {
    return '';
  }
}

/** Supabase column is `task_menu` (jsonb); Android field name is `task_menu_json`. */
export function getTaskMenuJsonString(service: { task_menu?: unknown; task_menu_json?: unknown }): string {
  const s = readJsonString(service.task_menu ?? service.task_menu_json);
  return s;
}

export function parseServiceTaskMenu(json: string | null | undefined): ParsedTaskMenu | null {
  const trimmed = (json ?? '').trim();
  if (!trimmed || trimmed === '{}' || trimmed === 'null') return null;
  try {
    if (trimmed.startsWith('[')) {
      return parseRoot({ rows: JSON.parse(trimmed) } as Record<string, unknown>);
    }
    return parseRoot(JSON.parse(trimmed) as Record<string, unknown>);
  } catch {
    return null;
  }
}

function parseRoot(root: Record<string, unknown>): ParsedTaskMenu | null {
  const banner = typeof root.banner_image_url === 'string' && root.banner_image_url.trim() ? root.banner_image_url.trim() : undefined;
  const arr = root.rows;
  if (!Array.isArray(arr)) return null;
  const out: TaskMenuRow[] = [];
  for (let i = 0; i < arr.length; i++) {
    const o = arr[i];
    if (!o || typeof o !== 'object') continue;
    const row = o as Record<string, unknown>;
    const t = String(row.type ?? '').trim().toLowerCase();
    if (t === 'section') {
      const title = String(row.title ?? '').trim();
      if (!title) continue;
      const subtitle = String(row.subtitle ?? '').trim() || undefined;
      out.push({ type: 'section', title, subtitle });
    } else if (t === 'quantity') {
      const key = String(row.key ?? '').trim() || `q_${i}`;
      const min = Math.max(0, Math.floor(Number(row.min ?? 0)) || 0);
      const maxRaw = Math.floor(Number(row.max ?? 99)) || 99;
      const max = Math.max(min, maxRaw);
      const def = clampInt(Math.floor(Number(row.default_qty ?? 0)) || 0, min, max);
      const price = Math.max(0, Number(row.unit_price ?? 0) || 0);
      out.push({
        type: 'quantity',
        key,
        title: String(row.title ?? '').trim() || key,
        unitLabel: String(row.unit_label ?? '').trim() || undefined,
        unitPrice: price,
        imageUrl: String(row.image_url ?? '').trim() || undefined,
        quantity: def,
        min,
        max,
      });
    } else if (t === 'toggle') {
      const key = String(row.key ?? '').trim() || `t_${i}`;
      const price = Math.max(0, Number(row.unit_price ?? 0) || 0);
      out.push({
        type: 'toggle',
        key,
        title: String(row.title ?? '').trim() || key,
        unitLabel: String(row.unit_label ?? '').trim() || undefined,
        unitPrice: price,
        imageUrl: String(row.image_url ?? '').trim() || undefined,
        checked: Boolean(row.default_checked),
      });
    }
  }
  if (!out.some((r) => r.type === 'quantity' || r.type === 'toggle')) return null;
  return { bannerImageUrl: banner, rows: out };
}

function clampInt(n: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, n));
}

export function buildQuoteLinesFromTaskMenuRows(rows: readonly TaskMenuRow[]): QuoteLine[] {
  const lines: QuoteLine[] = [];
  for (const row of rows) {
    if (row.type === 'quantity') {
      if (row.quantity > 0) {
        lines.push({ label: row.title, unitPrice: row.unitPrice, quantity: row.quantity });
      }
    } else if (row.type === 'toggle') {
      if (row.checked) {
        lines.push({ label: row.title, unitPrice: row.unitPrice, quantity: 1 });
      }
    }
  }
  return lines;
}

export function cloneTaskMenuRows(rows: readonly TaskMenuRow[]): TaskMenuRow[] {
  return rows.map((r) => {
    if (r.type === 'section') return { ...r };
    if (r.type === 'quantity') return { ...r };
    return { ...r };
  });
}
