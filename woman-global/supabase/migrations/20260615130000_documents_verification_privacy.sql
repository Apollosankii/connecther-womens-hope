-- Close cross-provider document leak: seekers may only read other providers' verified portfolio rows.
-- Application / KYC uploads stay visible only to the owner (documents_select_own) and admins.

DROP POLICY IF EXISTS "documents_seeker_read_portfolio" ON public.documents;
CREATE POLICY "documents_seeker_read_portfolio" ON public.documents
  FOR SELECT TO authenticated
  USING (
    verified IS TRUE
    AND user_id IS DISTINCT FROM public.current_user_pk()
    AND EXISTS (
      SELECT 1 FROM public.users u
      WHERE u.id = documents.user_id
        AND COALESCE(u.service_provider, false) = true
    )
  );

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
      JOIN public.users u ON u.id = d.user_id
      WHERE d.name IS NOT NULL
        AND (
          trim(both '/' FROM split_part(split_part(d.name, '/object/provider-docs/', 2), '?', 1))
            = trim(both '/' FROM storage.objects.name)
          OR d.name LIKE '%/' || storage.objects.name
        )
        AND (
          d.user_id = public.current_user_pk()
          OR (
            COALESCE(d.verified, false) = true
            AND d.user_id IS DISTINCT FROM public.current_user_pk()
            AND COALESCE(u.service_provider, false) = true
          )
        )
    )
  );
