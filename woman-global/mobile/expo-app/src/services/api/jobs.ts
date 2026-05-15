import { getSupabaseAuthedClient, getSupabasePublicClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';
import type { Job } from '@/types/models';

const JOB_SITE_PHOTOS_BUCKET = 'job_site_photos';

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

export async function completeJob(jobId: number): Promise<boolean> {
  if (!jobId) return false;
  const ok = await ensureSupabaseSession();
  if (!ok) return false;
  const supabase = getSupabaseAuthedClient();
  const { error } = await supabase.rpc('complete_my_job', { p_job_id: jobId });
  return !error;
}

type OkErrRpcRow = { ok?: boolean | null; err?: string | null };

async function callOkErr(rpcName: string, params: Record<string, any>): Promise<string | null> {
  const ok = await ensureSupabaseSession();
  if (!ok) return 'auth';
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc(rpcName, params);
  if (error) throw new Error(error.message);
  const row = ((data ?? []) as OkErrRpcRow[])[0];
  if (row?.ok) return null;
  return row?.err?.trim() || 'unknown';
}

export async function uploadJobSitePhoto(params: { jobId: number; firebaseUid: string; fileUri: string; fileName: string }) {
  const ok = await ensureSupabaseSession();
  if (!ok) return null;
  const { jobId, firebaseUid, fileUri } = params;
  if (!jobId || !firebaseUid.trim() || !fileUri.trim()) return null;
  const safe = (params.fileName || 'arrival.jpg').trim().replace(/[^a-zA-Z0-9._-]/g, '_') || 'arrival.jpg';
  const path = `${jobId}/${firebaseUid.trim()}/${Date.now()}_${safe}`;

  const resp = await fetch(fileUri);
  const blob = await resp.blob();

  const supabase = getSupabaseAuthedClient();
  const { error } = await supabase.storage.from(JOB_SITE_PHOTOS_BUCKET).upload(path, blob, { upsert: true });
  if (error) return null;
  return path;
}

export async function providerRecordJobArrival(jobId: number, sitePhotoPath: string): Promise<string | null> {
  return callOkErr('provider_record_job_arrival', { p_job_id: jobId, p_site_photo_path: sitePhotoPath });
}

export async function providerStartJobWork(jobId: number): Promise<string | null> {
  return callOkErr('provider_start_job_work', { p_job_id: jobId });
}

export async function providerSubmitJobCheckin(jobId: number, hourIndex: number, lat?: number, lng?: number): Promise<string | null> {
  return callOkErr('provider_submit_job_checkin', {
    p_job_id: jobId,
    p_hour_index: hourIndex,
    p_lat: typeof lat === 'number' ? lat : null,
    p_lng: typeof lng === 'number' ? lng : null,
  });
}

export async function providerIsBookable(userDbId: number): Promise<boolean> {
  if (!userDbId) return false;
  const supabase = getSupabasePublicClient();
  const { data, error } = await supabase.rpc('provider_is_bookable', { p_user_id: userDbId });
  if (error) return false;
  const row = ((data ?? []) as Array<{ bookable?: boolean | null }>)[0];
  return row?.bookable === true;
}

export async function myProviderHasActiveJob(): Promise<boolean> {
  const ok = await ensureSupabaseSession();
  if (!ok) return false;
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc('my_provider_has_active_job');
  if (error) return false;
  const row = ((data ?? []) as Array<{ busy?: boolean | null }>)[0];
  return row?.busy === true;
}

