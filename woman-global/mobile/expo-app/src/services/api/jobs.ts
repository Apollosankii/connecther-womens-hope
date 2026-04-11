import { getSupabaseAuthedClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';
import type { Job } from '@/types/models';

export async function listPendingJobs(): Promise<Job[]> {
  const ok = await ensureSupabaseSession();
  if (!ok) return [];
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc('get_pending_jobs');
  if (error) throw new Error(error.message);
  return (data ?? []) as Job[];
}

export async function listCompletedJobs(): Promise<Job[]> {
  const ok = await ensureSupabaseSession();
  if (!ok) return [];
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc('get_completed_jobs');
  if (error) throw new Error(error.message);
  return (data ?? []) as Job[];
}

