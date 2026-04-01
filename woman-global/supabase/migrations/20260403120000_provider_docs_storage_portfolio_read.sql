-- Let authenticated seekers open provider portfolio files in private provider-docs bucket
-- when a matching row exists in public.documents for a service_provider user (RLS on table already allows SELECT).

DROP POLICY IF EXISTS "provider_docs_portfolio_select_authenticated" ON storage.objects;

CREATE POLICY "provider_docs_portfolio_select_authenticated"
  ON storage.objects
  FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'provider-docs'
    AND EXISTS (
      SELECT 1
      FROM public.documents d
      JOIN public.users u ON u.id = d.user_id AND COALESCE(u.service_provider, false) = true
      WHERE d.name IS NOT NULL
        AND (
          trim(both '/' FROM split_part(split_part(d.name, '/object/provider-docs/', 2), '?', 1))
            = trim(both '/' FROM storage.objects.name)
          OR d.name LIKE '%/' || storage.objects.name
        )
    )
  );
