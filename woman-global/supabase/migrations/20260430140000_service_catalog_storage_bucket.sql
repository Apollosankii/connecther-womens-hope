-- Public bucket for service catalog images (admin portal uploads via service role; mobile app uses full URL in services.service_pic).
INSERT INTO storage.buckets (id, name, public)
VALUES ('service_catalog', 'service_catalog', true)
ON CONFLICT (id) DO UPDATE SET public = true;

DROP POLICY IF EXISTS "service_catalog_public_read" ON storage.objects;
CREATE POLICY "service_catalog_public_read"
  ON storage.objects FOR SELECT TO public
  USING (bucket_id = 'service_catalog');

DROP POLICY IF EXISTS "service_catalog_admin_all" ON storage.objects;
CREATE POLICY "service_catalog_admin_all"
  ON storage.objects FOR ALL TO authenticated
  USING (bucket_id = 'service_catalog' AND public.is_admin())
  WITH CHECK (bucket_id = 'service_catalog' AND public.is_admin());
