-- Document types for provider application verification (Android + Admin review).
-- Run in Supabase after `document_type` exists. Idempotent.

INSERT INTO public.document_type (name)
SELECT 'National ID / Driving licence'
WHERE NOT EXISTS (
  SELECT 1 FROM public.document_type WHERE name = 'National ID / Driving licence'
);

INSERT INTO public.document_type (name)
SELECT 'Professional certification'
WHERE NOT EXISTS (
  SELECT 1 FROM public.document_type WHERE name = 'Professional certification'
);
