/** Minimal PostgREST access with the service role (no @supabase/supabase-js; faster cold start). */

function baseUrl(supabaseUrl: string): string {
  return supabaseUrl.replace(/\/$/, "");
}

function serviceHeaders(serviceKey: string, extra?: Record<string, string>): HeadersInit {
  const h: Record<string, string> = {
    apikey: serviceKey,
    Authorization: `Bearer ${serviceKey}`,
  };
  if (extra) for (const [k, v] of Object.entries(extra)) h[k] = v;
  return h;
}

export async function restSelectFirst<T extends Record<string, unknown>>(
  supabaseUrl: string,
  serviceKey: string,
  table: string,
  filter: string,
  columns: string,
): Promise<{ row: T | null; error: string | null }> {
  const url =
    `${baseUrl(supabaseUrl)}/rest/v1/${table}?${filter}&select=${encodeURIComponent(columns)}&limit=1`;
  const resp = await fetch(url, { headers: serviceHeaders(serviceKey) });
  const text = await resp.text();
  if (!resp.ok) return { row: null, error: `${resp.status}: ${text}` };
  if (!text) return { row: null, error: null };
  let data: unknown;
  try {
    data = JSON.parse(text);
  } catch {
    return { row: null, error: `invalid JSON: ${text.slice(0, 200)}` };
  }
  if (!Array.isArray(data) || data.length === 0) return { row: null, error: null };
  return { row: data[0] as T, error: null };
}

export async function restInsert(
  supabaseUrl: string,
  serviceKey: string,
  table: string,
  body: Record<string, unknown>,
): Promise<{ error: string | null }> {
  const url = `${baseUrl(supabaseUrl)}/rest/v1/${table}`;
  const resp = await fetch(url, {
    method: "POST",
    headers: serviceHeaders(serviceKey, {
      "Content-Type": "application/json",
      Prefer: "return=minimal",
    }),
    body: JSON.stringify(body),
  });
  const text = await resp.text();
  if (!resp.ok) return { error: `${resp.status}: ${text}` };
  return { error: null };
}

export async function restPatch(
  supabaseUrl: string,
  serviceKey: string,
  table: string,
  filter: string,
  body: Record<string, unknown>,
): Promise<{ error: string | null }> {
  const url = `${baseUrl(supabaseUrl)}/rest/v1/${table}?${filter}`;
  const resp = await fetch(url, {
    method: "PATCH",
    headers: serviceHeaders(serviceKey, {
      "Content-Type": "application/json",
      Prefer: "return=minimal",
    }),
    body: JSON.stringify(body),
  });
  const text = await resp.text();
  if (!resp.ok) return { error: `${resp.status}: ${text}` };
  return { error: null };
}
