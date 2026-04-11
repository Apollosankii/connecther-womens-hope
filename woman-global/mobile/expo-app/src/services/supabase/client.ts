import { createClient, type SupabaseClient } from '@supabase/supabase-js';

import { AppConfig } from '@/services/config';
import { getSupabaseJwt } from '@/services/supabase/tokenStore';

function requireSupabaseConfig() {
  const url = AppConfig.supabaseUrl();
  const anonKey = AppConfig.supabaseAnonKey();
  if (!url || !anonKey) throw new Error('Missing Supabase configuration (EXPO_PUBLIC_SUPABASE_*).');
  return { url, anonKey };
}

let publicClient: SupabaseClient | null = null;
let authedClient: SupabaseClient | null = null;

export function getSupabasePublicClient(): SupabaseClient {
  if (publicClient) return publicClient;
  const { url, anonKey } = requireSupabaseConfig();
  publicClient = createClient(url, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
  });
  return publicClient;
}

/**
 * Authenticated client using the *bridge JWT* (minted by `auth-bridge`).
 * This intentionally does NOT use Supabase Auth sessions.
 */
export function getSupabaseAuthedClient(): SupabaseClient {
  if (authedClient) return authedClient;
  const { url, anonKey } = requireSupabaseConfig();
  authedClient = createClient(url, anonKey, {
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
    global: {
      fetch: async (input, init) => {
        const jwt = await getSupabaseJwt();
        const headers = new Headers(init?.headers ?? {});
        if (jwt) headers.set('Authorization', `Bearer ${jwt}`);
        return fetch(input, { ...init, headers });
      },
    },
  });
  return authedClient;
}

