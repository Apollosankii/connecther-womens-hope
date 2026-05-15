import { getSupabaseAuthedClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';

export async function reportProblem(description: string): Promise<boolean> {
  const ok = await ensureSupabaseSession();
  if (!ok) return false;
  const supabase = getSupabaseAuthedClient();
  const { error } = await supabase.rpc('insert_my_problem_report', { p_description: description });
  return !error;
}

export async function insertHelpRequest(): Promise<boolean> {
  const ok = await ensureSupabaseSession();
  if (!ok) return false;
  const supabase = getSupabaseAuthedClient();
  const { error } = await supabase.rpc('insert_my_help_request');
  return !error;
}

export async function reportGbvEmergency(params?: {
  latitude?: number | null;
  longitude?: number | null;
  locationText?: string | null;
}): Promise<boolean> {
  const ok = await ensureSupabaseSession();
  if (!ok) return false;
  const supabase = getSupabaseAuthedClient();
  const { error } = await supabase.rpc('insert_my_help_request', {
    p_latitude: typeof params?.latitude === 'number' ? params?.latitude : null,
    p_longitude: typeof params?.longitude === 'number' ? params?.longitude : null,
    p_location_text: params?.locationText ?? null,
  });
  return !error;
}

