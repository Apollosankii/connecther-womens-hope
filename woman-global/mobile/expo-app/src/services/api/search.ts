import { getSupabasePublicClient } from '@/services/supabase/client';

export async function searchProviders(query: string) {
  const q = query.trim();
  if (!q) return [];
  const supabase = getSupabasePublicClient();
  const pattern = `%${q}%`;
  const { data, error } = await supabase
    .from('users')
    .select('*')
    .or(`first_name.ilike.${pattern},last_name.ilike.${pattern},occupation.ilike.${pattern},county.ilike.${pattern}`)
    .limit(30);
  if (error) return [];
  return (data ?? []).filter((u: any) => u.service_provider === true);
}

