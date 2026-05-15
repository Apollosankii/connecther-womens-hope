/**
 * PDFs and Office types often fail or hang in a plain WebView. We try viewers in order
 * (Mozilla pdf.js for PDF first — more reliable than Google for signed URLs — then Google
 * embedded viewer, then direct URL as a last resort). Matches Android intent in
 * `SecureProviderDocumentActivity` but avoids a single flaky gviewer load.
 */
const OFFICE_LIKE_EXTENSIONS = [
  '.pdf',
  '.doc',
  '.docx',
  '.ppt',
  '.pptx',
  '.xls',
  '.xlsx',
  '.odt',
  '.ods',
  '.odp',
];

function pathWithoutQueryLower(url: string): string {
  return (url.split('?')[0] ?? '').toLowerCase();
}

function endsWithOfficeLike(pathLower: string): boolean {
  return OFFICE_LIKE_EXTENSIONS.some((ext) => pathLower.endsWith(ext));
}

function isPdf(pathLower: string): boolean {
  return pathLower.endsWith('.pdf');
}

function googleGviewerUrl(signedUrl: string): string {
  return `https://docs.google.com/gviewer?embedded=true&url=${encodeURIComponent(signedUrl)}`;
}

function mozillaPdfJsViewerUrl(signedUrl: string): string {
  return `https://mozilla.github.io/pdf.js/web/viewer.html?file=${encodeURIComponent(signedUrl)}`;
}

function uniqUrls(urls: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const u of urls) {
    const t = u.trim();
    if (!t || seen.has(t)) continue;
    seen.add(t);
    out.push(t);
  }
  return out;
}

/**
 * Ordered list of URLs to try in the WebView (advance on timeout / HTTP error as needed).
 */
export function buildDocumentViewerChain(raw: string): string[] {
  const signed = raw.trim();
  if (!signed) return [];
  const pathLower = pathWithoutQueryLower(signed);
  if (!endsWithOfficeLike(pathLower)) {
    return [signed];
  }
  if (isPdf(pathLower)) {
    return uniqUrls([mozillaPdfJsViewerUrl(signed), googleGviewerUrl(signed), signed]);
  }
  return uniqUrls([googleGviewerUrl(signed), signed]);
}

/** @deprecated Prefer buildDocumentViewerChain for fallbacks; kept for single-URL callers */
export function viewerUrlForSignedStorageLink(raw: string): string {
  const chain = buildDocumentViewerChain(raw);
  return chain[0] ?? raw.trim();
}

export function isMozillaPdfJsViewerUrl(uri: string): boolean {
  return uri.includes('mozilla.github.io/pdf.js/') && uri.includes('viewer.html');
}

const IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.heic'];

/** Signed storage URLs for photos — render with Image, not WebView. */
export function isDirectImageUrl(raw: string): boolean {
  const pathLower = pathWithoutQueryLower(raw);
  return IMAGE_EXTENSIONS.some((ext) => pathLower.endsWith(ext));
}
