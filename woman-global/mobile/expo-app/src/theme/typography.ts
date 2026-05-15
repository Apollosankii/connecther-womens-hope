/**
 * Typography tokens ported from Android layouts (sp) + common Material weight usage.
 * Keep these centralized so screens can match Android consistently.
 */
export const Typography = {
  // App + section headers
  appTitle: {
    fontSize: 20,
    fontWeight: '800',
  },
  h1: {
    fontSize: 28,
    fontWeight: '800',
  },
  h2: {
    fontSize: 26,
    fontWeight: '800',
  },
  h3: {
    fontSize: 20,
    fontWeight: '800',
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '800',
  },
  rowTitle: {
    fontSize: 16,
    fontWeight: '700',
  },

  // Body text
  body: {
    fontSize: 14,
    lineHeight: 20,
  },
  bodyStrong: {
    fontSize: 14,
    fontWeight: '600',
    lineHeight: 20,
  },
  caption: {
    fontSize: 13,
    lineHeight: 18,
  },
  link: {
    fontSize: 14,
    fontWeight: '700',
  },
  overline: {
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 0.12,
    textTransform: 'uppercase' as const,
  },
} as const;

