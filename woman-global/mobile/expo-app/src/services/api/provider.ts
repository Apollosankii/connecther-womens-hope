import { getSupabaseAuthedClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';

export async function submitProviderApplication(payload: {
  gender?: string;
  birthDate?: string; // YYYY-MM-DD
  country?: string;
  county?: string;
  areaName?: string;
  natId?: string;
  emmCont1?: string;
  emmCont2?: string;
  serviceIds?: number[];
  workingHours?: string;
  professionalTitle?: string;
  experience?: string;
}) {
  const ok = await ensureSupabaseSession();
  if (!ok) return { ok: false as const, errorCode: 'auth_required' as const };
  const supabase = getSupabaseAuthedClient();
  const serviceIdsText = payload.serviceIds?.length ? JSON.stringify(payload.serviceIds) : '';
  const { error } = await supabase.rpc('submit_provider_application', {
    p_gender: payload.gender ?? null,
    p_birth_date: payload.birthDate ?? null,
    p_country: payload.country ?? null,
    p_county: payload.county ?? null,
    p_area_name: payload.areaName ?? null,
    p_nat_id: payload.natId ?? null,
    p_emm_cont_1: payload.emmCont1 ?? null,
    p_emm_cont_2: payload.emmCont2 ?? null,
    p_service_ids: serviceIdsText || null,
    p_working_hours: payload.workingHours ?? null,
    p_professional_title: payload.professionalTitle ?? null,
    p_experience: payload.experience ?? null,
  });
  if (error) throw new Error(error.message);
  return { ok: true as const };
}

