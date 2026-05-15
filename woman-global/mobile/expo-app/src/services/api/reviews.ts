import { getSupabaseAuthedClient, getSupabasePublicClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';

export type SubmitJobReviewResult = { ok: boolean; err: string | null };

export async function submitJobReview(jobId: number, stars: number, reviewText?: string) {
  const ok = await ensureSupabaseSession();
  if (!ok) return { ok: false as const, errorCode: 'auth_required' as const };
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.rpc('submit_job_review', {
    p_job_id: jobId,
    p_stars: stars,
    p_review_text: reviewText ?? null,
  });
  if (error) throw new Error(error.message);
  const row = ((data ?? []) as Array<{ ok?: boolean | null; err?: string | null }>)[0];
  if (!row?.ok) return { ok: false as const, errorCode: row?.err ?? 'unknown' };
  return { ok: true as const };
}

async function reviewsClient() {
  const ok = await ensureSupabaseSession();
  return ok ? getSupabaseAuthedClient() : getSupabasePublicClient();
}

export async function getPublicRatingStatsForUser(userId: number): Promise<{ avgStars: number; reviewCount: number } | null> {
  if (!userId) return null;
  const supabase = await reviewsClient();
  const { data, error } = await supabase.rpc('get_public_rating_stats_for_user', { p_user_id: userId });
  if (error) throw new Error(error.message);
  const row = ((data ?? []) as Array<{ avg_stars?: number | string | null; review_count?: number | null }>)[0];
  if (!row) return null;
  const avg = typeof row.avg_stars === 'string' ? Number(row.avg_stars) : (row.avg_stars as number);
  return { avgStars: Number.isFinite(avg) ? avg : 0, reviewCount: row.review_count ?? 0 };
}

export type PublicReviewItem = { stars: number; reviewText: string; serviceName: string; createdAt: string; reviewerFirstName: string };

export async function listPublicReviewsForUser(userId: number, limit = 50): Promise<PublicReviewItem[]> {
  if (!userId) return [];
  const supabase = await reviewsClient();
  const { data, error } = await supabase.rpc('list_public_reviews_for_user', { p_user_id: userId, p_limit: limit });
  if (error) throw new Error(error.message);
  const rows = (data ?? []) as Array<{
    stars: number;
    review_text: string;
    service_name: string;
    created_at: string;
    reviewer_first_name: string;
  }>;
  return rows.map((r) => ({
    stars: r.stars,
    reviewText: r.review_text ?? '',
    serviceName: r.service_name ?? '',
    createdAt: r.created_at,
    reviewerFirstName: r.reviewer_first_name ?? '',
  }));
}

