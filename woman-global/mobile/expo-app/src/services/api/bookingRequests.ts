import { getSupabaseAuthedClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';
import type { BookingRequest } from '@/types/models';

/** Raw RPC row: flat jsonb, json string, or PostgREST envelope `{ get_my_booking_requests: ... }`. */
function parseJsonObject(raw: unknown): Record<string, unknown> | null {
  if (raw == null) return null;
  if (typeof raw === 'string') {
    try {
      return JSON.parse(raw) as Record<string, unknown>;
    } catch {
      return null;
    }
  }
  if (typeof raw === 'object' && !Array.isArray(raw)) {
    return raw as Record<string, unknown>;
  }
  return null;
}

function unwrapBookingRpcRow(raw: unknown): Record<string, unknown> | null {
  const obj = parseJsonObject(raw);
  if (!obj) return null;
  const inner = obj.get_my_booking_requests;
  if (inner != null) {
    const innerObj = parseJsonObject(inner);
    if (innerObj) return innerObj;
  }
  return obj;
}

function toNum(v: unknown): number | null {
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  if (typeof v === 'string' && v.trim() !== '') {
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

function mapRpcRow(r: Record<string, unknown>): BookingRequest {
  const id = toNum(r.id) ?? 0;
  const serviceId = toNum(r.service_id) ?? 0;
  const role = typeof r.role === 'string' ? r.role.toLowerCase() : '';
  const explicitClient = r.i_am_client;
  const i_am_client =
    typeof explicitClient === 'boolean'
      ? explicitClient
      : role === 'client'
        ? true
        : role === 'provider'
          ? false
          : null;

  return {
    id,
    service_id: serviceId,
    status: String(r.status ?? 'pending'),
    proposed_price: toNum(r.proposed_price),
    location_text: r.location_text != null ? String(r.location_text) : null,
    message: r.message != null ? String(r.message) : null,
    expires_at: r.expires_at != null ? String(r.expires_at) : null,
    created_at: r.created_at != null ? String(r.created_at) : null,
    client_display: r.client_display != null ? String(r.client_display) : null,
    provider_display: r.provider_display != null ? String(r.provider_display) : null,
    i_am_client,
    role: role || undefined,
    maps_url: r.maps_url != null ? String(r.maps_url) : null,
    latitude: toNum(r.latitude),
    longitude: toNum(r.longitude),
    location_extra: r.location_extra ?? null,
  };
}

export async function listMyBookingRequests(): Promise<BookingRequest[]> {
  const ok = await ensureSupabaseSession();
  if (!ok) return [];
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc('get_my_booking_requests');
  if (error) throw new Error(error.message);
  const rawRows = (data ?? []) as unknown[];
  const out: BookingRequest[] = [];
  for (const raw of rawRows) {
    const row = unwrapBookingRpcRow(raw);
    if (row) out.push(mapRpcRow(row));
  }
  return out;
}

type BookingActionRpcRow = {
  quote_id?: string | null;
  chat_code?: string | null;
  job_id?: string | null;
  err?: string | null;
};

async function bookingAction(rpcName: string, requestId: number) {
  const ok = await ensureSupabaseSession();
  if (!ok) return { ok: false as const, errorCode: 'auth_required' as const };
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc(rpcName, { p_request_id: requestId });
  if (error) throw new Error(error.message);
  const row = ((data ?? []) as BookingActionRpcRow[])[0];
  if (row?.err) return { ok: false as const, errorCode: row.err, chatCode: row.chat_code ?? null };
  return { ok: true as const, chatCode: row?.chat_code ?? null, quoteId: row?.quote_id ?? null, jobId: row?.job_id ?? null };
}

export async function acceptBookingRequest(requestId: number) {
  return bookingAction('accept_booking_request', requestId);
}

export async function declineBookingRequest(requestId: number) {
  return bookingAction('decline_booking_request', requestId);
}

export async function cancelBookingRequest(requestId: number) {
  return bookingAction('cancel_booking_request', requestId);
}
