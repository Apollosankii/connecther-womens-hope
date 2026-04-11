import { getSupabaseAuthedClient, getSupabasePublicClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';
import type { ActiveSubscription, SubscriptionPlan } from '@/types/models';

export async function listActiveSubscriptionPlans(): Promise<SubscriptionPlan[]> {
  const supabase = getSupabasePublicClient();
  const { data, error } = await supabase
    .from('subscription_plans')
    .select('*')
    .eq('is_active', true)
    .order('sort_order', { ascending: true });
  if (error) throw new Error(error.message);
  return (data ?? []) as SubscriptionPlan[];
}

/**
 * Fetch current user's active subscription (read-only; no payments/checkout in this app).
 * Android uses RPC/table lookups; this implementation assumes there is either:
 * - an RPC `get_active_subscription`, OR
 * - a table accessible via RLS (adjust when we confirm your schema).
 */
export async function getMyActiveSubscription(): Promise<ActiveSubscription | null> {
  const ok = await ensureSupabaseSession();
  if (!ok) return null;

  const supabase = getSupabaseAuthedClient();

  // Prefer RPC if it exists in your schema.
  const rpcRes = await supabase.rpc('get_active_subscription');
  if (!rpcRes.error) {
    const rows = (rpcRes.data ?? []) as any[];
    return (rows[0] ?? null) as ActiveSubscription | null;
  }

  // Fallback: common table name (adjust to your actual schema if different).
  const { data, error } = await supabase
    .from('plan_subscriptions')
    .select('*')
    .in('status', ['active', 'trialing'])
    .order('id', { ascending: false })
    .limit(1);
  if (error) return null;
  return ((data ?? [])[0] ?? null) as ActiveSubscription | null;
}

