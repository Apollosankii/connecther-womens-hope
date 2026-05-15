import { getMyUserProfile, getMyUserId } from '@/services/api/profile';
import { AppConfig } from '@/services/config';
import { getSupabaseAuthedClient } from '@/services/supabase/client';
import { ensureSupabaseSession } from '@/services/supabase/session';

const PROVIDER_DOCS_BUCKET = 'provider-docs';

export type VerificationDocumentItem = {
  id: number;
  docTypeName: string;
  verified: boolean;
  fileLabel: string;
  signedUrl: string | null;
};

type DbDocumentTypeRow = { id: number; name?: string | null };
type DbDocumentRow = { id: number; name?: string | null; doc_type_id?: number | null; verified?: boolean | null };

function storagePathFromStoredDocName(name: string | null | undefined, bucketId: string): string | null {
  if (!name) return null;
  const n = name.trim();
  if (!n) return null;
  if (!n.includes('://')) return n.replace(/^\/+/, '');
  const markers = [`/storage/v1/object/${bucketId}/`, `/storage/v1/object/public/${bucketId}/`];
  for (const m of markers) {
    const idx = n.toLowerCase().indexOf(m.toLowerCase());
    if (idx >= 0) return n.slice(idx + m.length).split('?')[0].split('#')[0];
  }
  return null;
}

async function getDocTypeNames(): Promise<Map<number, string>> {
  const supabase = getSupabaseAuthedClient();
  const { data } = await supabase.from('document_type').select('id,name');
  const rows = (data ?? []) as DbDocumentTypeRow[];
  const m = new Map<number, string>();
  for (const r of rows) m.set(r.id, (r.name ?? '').trim());
  return m;
}

async function signedUrlForPath(path: string): Promise<string | null> {
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase.storage.from(PROVIDER_DOCS_BUCKET).createSignedUrl(path, 60 * 60);
  if (error) return null;
  return data?.signedUrl ?? null;
}

async function getMyInternalUserDbId(): Promise<number | null> {
  const profile = await getMyUserProfile();
  const id = profile?.id;
  return typeof id === 'number' && id > 0 ? id : null;
}

/** Current user's verification uploads only (never other providers' rows). */
export async function listMyVerificationDocuments(): Promise<VerificationDocumentItem[]> {
  const ok = await ensureSupabaseSession();
  if (!ok) return [];
  const userId = await getMyInternalUserDbId();
  if (!userId) return [];

  const supabase = getSupabaseAuthedClient();
  const typeNames = await getDocTypeNames().catch(() => new Map<number, string>());
  const { data, error } = await supabase
    .from('documents')
    .select('id,name,doc_type_id,verified')
    .eq('user_id', userId)
    .order('id', { ascending: false });
  if (error) throw new Error(error.message);
  const rows = (data ?? []) as DbDocumentRow[];

  const out: VerificationDocumentItem[] = [];
  for (const row of rows) {
    const path = storagePathFromStoredDocName(row.name ?? null, PROVIDER_DOCS_BUCKET);
    if (!path) continue;
    const label = path.split('/').pop() || `document-${row.id}`;
    const typeNameRaw = row.doc_type_id ? typeNames.get(row.doc_type_id) : '';
    const docTypeName = typeNameRaw?.trim() ? typeNameRaw.trim() : `Document #${row.doc_type_id ?? '?'}`;
    out.push({
      id: row.id,
      docTypeName,
      verified: row.verified === true,
      fileLabel: label,
      signedUrl: null,
    });
  }
  return out;
}

/** Open own verification document in-app (must belong to current user). */
export async function getSignedUrlForMyVerificationDocument(documentId: number): Promise<string | null> {
  if (!documentId) return null;
  const userId = await getMyInternalUserDbId();
  if (!userId) return null;
  const ok = await ensureSupabaseSession();
  if (!ok) return null;

  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase
    .from('documents')
    .select('id,name,user_id')
    .eq('id', documentId)
    .eq('user_id', userId)
    .maybeSingle();
  if (error || data == null) return null;

  const row = data as { name?: string | null };
  const path = storagePathFromStoredDocName(row.name ?? null, PROVIDER_DOCS_BUCKET);
  if (!path) return null;
  return signedUrlForPath(path).catch(() => null);
}

export async function listProviderPortfolioDocuments(providerUserDbId: number): Promise<VerificationDocumentItem[]> {
  if (!providerUserDbId) return [];
  const ok = await ensureSupabaseSession();
  if (!ok) return [];
  const supabase = getSupabaseAuthedClient();

  const typeNames = await getDocTypeNames().catch(() => new Map<number, string>());
  const { data, error } = await supabase
    .from('documents')
    .select('id,name,doc_type_id,verified')
    .eq('user_id', providerUserDbId)
    .order('id', { ascending: false });
  if (error) throw new Error(error.message);
  const rows = (data ?? []) as DbDocumentRow[];

  const out: VerificationDocumentItem[] = [];
  for (const row of rows) {
    const path = storagePathFromStoredDocName(row.name ?? null, PROVIDER_DOCS_BUCKET);
    if (!path) continue;
    const label = path.split('/').pop() || `document-${row.id}`;
    const typeNameRaw = row.doc_type_id ? typeNames.get(row.doc_type_id) : '';
    const docTypeName = typeNameRaw?.trim() ? typeNameRaw.trim() : `Document #${row.doc_type_id ?? '?'}`;
    /** Signed URLs are created only when the user opens a doc — avoids background GETs / download prompts on profile. */
    out.push({
      id: row.id,
      docTypeName,
      verified: row.verified === true,
      fileLabel: label,
      signedUrl: null,
    });
  }
  return out;
}

/** One-off signed URL when opening a portfolio row (must belong to `ownerUserDbId`). */
export async function getSignedUrlForProviderPortfolioDocument(
  documentId: number,
  ownerUserDbId: number,
): Promise<string | null> {
  if (!documentId || !ownerUserDbId) return null;
  const ok = await ensureSupabaseSession();
  if (!ok) return null;
  const supabase = getSupabaseAuthedClient();
  const { data, error } = await supabase
    .from('documents')
    .select('id,name,user_id')
    .eq('id', documentId)
    .eq('user_id', ownerUserDbId)
    .maybeSingle();
  if (error || data == null) return null;
  const row = data as { id?: number; name?: string | null; user_id?: number | null };
  const path = storagePathFromStoredDocName(row.name ?? null, PROVIDER_DOCS_BUCKET);
  if (!path) return null;
  return signedUrlForPath(path).catch(() => null);
}

export async function uploadProviderVerificationDocument(params: { fileUri: string; fileName: string; docTypeLabel: string }) {
  const ok = await ensureSupabaseSession();
  if (!ok) return false;
  const supabase = getSupabaseAuthedClient();

  const userRef = await getMyUserId();
  if (!userRef) return false;

  const { data: userRows, error: userErr } = await supabase.from('users').select('id').eq('user_id', userRef).limit(1);
  if (userErr) return false;
  const internalUserId = (userRows?.[0] as any)?.id as number | undefined;
  if (!internalUserId) return false;

  const normalized = params.docTypeLabel.trim().toLowerCase();
  const { data: types, error: typeErr } = await supabase.from('document_type').select('id,name');
  if (typeErr) return false;
  const typeRows = (types ?? []) as DbDocumentTypeRow[];
  const chosen =
    typeRows.find((r) => {
      const n = (r.name ?? '').toLowerCase();
      return (n && normalized && (n.includes(normalized) || normalized.includes(n))) || false;
    }) ?? typeRows[0];
  if (!chosen?.id) return false;

  const safeName = (params.fileName || 'document').trim().replace(/[^a-zA-Z0-9._-]/g, '_') || 'document';
  const path = `provider_docs/${userRef}/${Date.now()}_${safeName}`;

  const resp = await fetch(params.fileUri);
  const blob = await resp.blob();
  const up = await supabase.storage.from(PROVIDER_DOCS_BUCKET).upload(path, blob, { upsert: true });
  if (up.error) return false;

  const base = AppConfig.supabaseUrl().replace(/\/+$/, '');
  const docUrl = `${base}/storage/v1/object/${PROVIDER_DOCS_BUCKET}/${path}`;

  const { error: insErr } = await supabase.from('documents').insert({
    user_id: internalUserId,
    doc_type_id: chosen.id,
    name: docUrl,
    verified: false,
  });
  return !insErr;
}

