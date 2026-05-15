import { getSupabaseAuthedClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';

type CreateBookingRpcRow = {
  request_id?: number | null;
  err?: string | null;
};

export async function createBookingRequest(params: {
  providerRef: string;
  serviceId: number;
  proposedPrice: number;
  locationText?: string;
  message?: string;
  latitude?: number;
  longitude?: number;
  /** Sent as JSONB; required non-empty keys when `services.require_location_detail` is true. */
  locationExtra?: Record<string, string>;
}) {
  const ok = await ensureSupabaseSession();
  if (!ok) return { ok: false as const, errorCode: 'auth_required' as const };
  const supabase = getSupabaseAuthedClient();
  const extra = params.locationExtra && Object.keys(params.locationExtra).length > 0 ? params.locationExtra : {};
  const { data, error } = await supabase.rpc('create_booking_request', {
    p_provider_ref: params.providerRef,
    p_service_id: params.serviceId,
    p_proposed_price: params.proposedPrice,
    p_location_text: params.locationText ?? '',
    p_message: params.message ?? '',
    p_lat: params.latitude ?? null,
    p_lng: params.longitude ?? null,
    p_location_extra: extra,
  });
  if (error) throw new Error(error.message);
  const row = ((data ?? []) as CreateBookingRpcRow[])[0];
  if (row?.request_id && !row.err) return { ok: true as const };
  return { ok: false as const, errorCode: (row?.err ?? 'request_failed') as string };
}

