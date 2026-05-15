import { getSupabaseAuthedClient, getSupabasePublicClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';
import type { Provider } from '@/types/models';

function numField(v: unknown): number | null {
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  if (typeof v === 'string' && v.trim()) {
    const n = Number(v);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}

function coerceRecord(raw: unknown): Record<string, unknown> | null {
  if (raw == null) return null;
  if (typeof raw === 'object' && !Array.isArray(raw)) return raw as Record<string, unknown>;
  if (typeof raw === 'string') {
    try {
      const p = JSON.parse(raw) as unknown;
      return typeof p === 'object' && p != null && !Array.isArray(p) ? (p as Record<string, unknown>) : null;
    } catch {
      return null;
    }
  }
  return null;
}

function mapProviderRow(r: Record<string, unknown>): Provider | null {
  const id = numField(r.id);
  if (id == null) return null;
  return {
    id,
    user_name: (typeof r.user_name === 'string' ? r.user_name : null) ?? null,
    first_name: (typeof r.first_name === 'string' ? r.first_name : null) ?? null,
    last_name: (typeof r.last_name === 'string' ? r.last_name : null) ?? null,
    title: (typeof r.title === 'string' ? r.title : null) ?? null,
    phone: (typeof r.phone === 'string' ? r.phone : null) ?? null,
    pic: (typeof r.pic === 'string' ? r.pic : null) ?? null,
    area_name: (typeof r.area_name === 'string' ? r.area_name : null) ?? null,
    country: (typeof r.country === 'string' ? r.country : null) ?? null,
    county: (typeof r.county === 'string' ? r.county : null) ?? null,
    occupation: (typeof r.occupation === 'string' ? r.occupation : null) ?? null,
    working_hours: (typeof r.working_hours === 'string' ? r.working_hours : null) ?? null,
    wh_badge: (typeof r['WH Badge'] === 'string' ? r['WH Badge'] : null) ?? null,
    latitude: numField(r.latitude),
    longitude: numField(r.longitude),
  };
}

function decodeProviderRpcList(data: unknown): Provider[] {
  const rows = Array.isArray(data) ? data : [];
  const out: Provider[] = [];
  for (const raw of rows) {
    const rec = coerceRecord(raw);
    if (!rec) continue;
    const p = mapProviderRow(rec);
    if (p) out.push(p);
  }
  return out;
}

/** All providers for a service (no geo filter). */
export async function listProvidersForService(serviceId: number): Promise<Provider[]> {
  const supabase = getSupabasePublicClient();
  const { data, error } = await supabase.rpc('get_providers_for_service', { p_service_id: serviceId });
  if (error) throw new Error(error.message);
  return decodeProviderRpcList(data);
}

/**
 * Providers within `services.search_radius_meters` of the seeker (PostGIS), using live locations.
 * Server reads radius from `services` — matches Kotlin `get_providers_for_service_near`.
 */
export async function listProvidersForServiceNear(serviceId: number, lat: number, lng: number): Promise<Provider[]> {
  const supabase = getSupabasePublicClient();
  const { data, error } = await supabase.rpc('get_providers_for_service_near', {
    p_service_id: serviceId,
    p_lat: lat,
    p_lng: lng,
  });
  if (error) throw new Error(error.message);
  return decodeProviderRpcList(data);
}

/** Seeker view of a provider row (Kotlin `ProfileActivity` user bind). Requires auth session. */
export async function getProviderUserById(providerId: number): Promise<Provider | null> {
  if (providerId < 1) return null;
  const ok = await ensureSupabaseSession();
  if (!ok) return null;
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase
    .from('users')
    .select(
      'id, user_id, first_name, last_name, title, occupation, working_hours, prof_pic, area_name, country, county, WH_badge',
    )
    .eq('id', providerId)
    .eq('service_provider', true)
    .maybeSingle();
  if (error || !data) return null;
  const r = data as Record<string, unknown>;
  return mapProviderRow({
    ...r,
    user_name: r.user_id,
    pic: r.prof_pic,
    'WH Badge': r.WH_badge,
  });
}

type StartConversationRpcRow = {
  quote_id?: string | null;
  chat_code?: string | null;
  err?: string | null;
};

export async function startConversationWithProvider(providerRef: string, serviceId: number) {
  const ok = await ensureSupabaseSession();
  if (!ok) return { chatCode: null as string | null, errorCode: 'auth_required' as string | null };
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc('start_conversation_with_provider', {
    p_provider_ref: providerRef,
    p_service_id: serviceId,
  });
  if (error) throw new Error(error.message);
  const row = ((data ?? []) as StartConversationRpcRow[])[0];
  return { chatCode: row?.chat_code ?? null, errorCode: row?.err ?? null, quoteId: row?.quote_id ?? null };
}
