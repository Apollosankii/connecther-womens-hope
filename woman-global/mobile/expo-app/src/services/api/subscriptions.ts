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

function parseLocalDatePrefix(s?: string | null): Date | null {
  if (!s) return null;
  const prefix = s.trim().slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(prefix)) return null;
  const d = new Date(`${prefix}T00:00:00`);
  return Number.isNaN(d.getTime()) ? null : d;
}

function isBetweenInclusive(today: Date, start: Date, end: Date) {
  const t = today.getTime();
  return start.getTime() <= t && t <= end.getTime();
}

export async function getMyActiveSubscription(): Promise<ActiveSubscription | null> {
  const ok = await ensureSupabaseSession();
  if (!ok) return null;

  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase
    .from('user_plan_subscriptions')
    .select('id, plan_id, status, payment_reference, started_at, expires_at, connects_granted, connects_used')
    .eq('status', 'active')
    .order('expires_at', { ascending: false })
    .limit(20);
  if (error) return null;

  const rows = (data ?? []) as Array<{
    plan_id: number;
    started_at?: string | null;
    expires_at?: string | null;
    connects_granted?: number | null;
    connects_used?: number | null;
  }>;

  const today = new Date();
  const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  const active = rows.find((r) => {
    const s = parseLocalDatePrefix(r.started_at ?? null);
    const e = parseLocalDatePrefix(r.expires_at ?? null);
    if (!s || !e) return false;
    return isBetweenInclusive(startOfToday, s, e);
  });
  if (!active) return null;

  const plans = await listActiveSubscriptionPlans().catch(() => [] as SubscriptionPlan[]);
  const planName = plans.find((p) => p.id === active.plan_id)?.name ?? `Plan #${active.plan_id}`;

  return {
    planId: active.plan_id,
    planName,
    expiresAt: active.expires_at ?? null,
    connectsGranted: active.connects_granted ?? null,
    connectsUsed: active.connects_used ?? null,
  };
}

export async function hasActiveSubscriptionForPayment(planId: number, paymentReference: string): Promise<boolean> {
  if (planId < 1 || !paymentReference.trim()) return false;
  const ok = await ensureSupabaseSession();
  if (!ok) return false;
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase
    .from('user_plan_subscriptions')
    .select('id')
    .eq('plan_id', planId)
    .eq('status', 'active')
    .eq('payment_reference', paymentReference)
    .limit(1);
  if (error) return false;
  return (data ?? []).length > 0;
}

