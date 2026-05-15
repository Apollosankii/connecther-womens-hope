import { getSupabasePublicClient } from '@/services/supabase/client';
import type { Service } from '@/types/models';

export async function listServices(): Promise<Service[]> {
  const supabase = getSupabasePublicClient();
  const { data, error } = await supabase.from('services').select('*');
  if (error) throw new Error(error.message);
  return (data ?? []) as Service[];
}

export async function getServiceById(id: number): Promise<Service | null> {
  const supabase = getSupabasePublicClient();
  const { data, error } = await supabase.from('services').select('*').eq('id', id).maybeSingle();
  if (error) throw new Error(error.message);
  return (data ?? null) as Service | null;
}

