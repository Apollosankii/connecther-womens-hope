-- Bidirectional job reviews: seekers and providers rate each other after job completion.
-- Replaces reliance on job_score_card for new ratings (table kept for any legacy rows).

-- ---------------------------------------------------------------------------
-- Table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.job_reviews (
  id bigserial PRIMARY KEY,
  job_id integer NOT NULL REFERENCES public.jobs (id) ON DELETE CASCADE,
  reviewer_user_id integer NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
  reviewee_user_id integer NOT NULL REFERENCES public.users (id) ON DELETE CASCADE,
  stars smallint NOT NULL CHECK (stars >= 1 AND stars <= 5),
  review_text text,
  is_public boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT job_reviews_one_per_reviewer UNIQUE (job_id, reviewer_user_id),
  CONSTRAINT job_reviews_no_self CHECK (reviewer_user_id <> reviewee_user_id)
);

CREATE INDEX IF NOT EXISTS idx_job_reviews_job_id ON public.job_reviews (job_id);
CREATE INDEX IF NOT EXISTS idx_job_reviews_reviewee_public ON public.job_reviews (reviewee_user_id)
  WHERE is_public = true;

COMMENT ON TABLE public.job_reviews IS 'Per-job ratings: reviewer -> reviewee. is_public=false when reviewee is client (privacy).';

-- ---------------------------------------------------------------------------
-- Submit review (SECURITY DEFINER)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.submit_job_review(
  p_job_id integer,
  p_stars integer,
  p_review_text text DEFAULT NULL
)
RETURNS TABLE (ok boolean, err text)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_me integer;
  v_client_id integer;
  v_provider_id integer;
  v_reviewee integer;
  v_is_public boolean;
  v_txt text;
BEGIN
  v_me := current_user_pk();
  IF v_me IS NULL THEN
    RETURN QUERY SELECT false, 'auth_required'::text;
    RETURN;
  END IF;

  IF p_stars IS NULL OR p_stars < 1 OR p_stars > 5 THEN
    RETURN QUERY SELECT false, 'invalid_stars'::text;
    RETURN;
  END IF;

  v_txt := NULLIF(trim(coalesce(p_review_text, '')), '');
  IF v_txt IS NOT NULL AND length(v_txt) > 2000 THEN
    RETURN QUERY SELECT false, 'review_text_too_long'::text;
    RETURN;
  END IF;

  SELECT q.client_id, q.provider_id
  INTO v_client_id, v_provider_id
  FROM public.jobs j
  JOIN public.quotes q ON q.id = j.quote_id
  WHERE j.id = p_job_id;

  IF v_client_id IS NULL THEN
    RETURN QUERY SELECT false, 'job_not_found'::text;
    RETURN;
  END IF;

  IF NOT (
    v_me = v_client_id OR v_me = v_provider_id
  ) THEN
    RETURN QUERY SELECT false, 'not_participant'::text;
    RETURN;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM public.jobs j WHERE j.id = p_job_id AND COALESCE(j.complete, false) = true
  ) THEN
    RETURN QUERY SELECT false, 'job_not_complete'::text;
    RETURN;
  END IF;

  IF v_me = v_client_id THEN
    v_reviewee := v_provider_id;
    v_is_public := true;
  ELSE
    v_reviewee := v_client_id;
    v_is_public := false;
  END IF;

  INSERT INTO public.job_reviews (job_id, reviewer_user_id, reviewee_user_id, stars, review_text, is_public)
  VALUES (p_job_id, v_me, v_reviewee, p_stars::smallint, v_txt, v_is_public);

  RETURN QUERY SELECT true, NULL::text;

EXCEPTION
  WHEN unique_violation THEN
    RETURN QUERY SELECT false, 'already_rated'::text;
END;
$$;

REVOKE ALL ON TABLE public.job_reviews FROM PUBLIC;
GRANT SELECT ON TABLE public.job_reviews TO authenticated;
GRANT SELECT ON TABLE public.job_reviews TO service_role;

ALTER TABLE public.job_reviews ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS job_reviews_select_participant_or_public ON public.job_reviews;
CREATE POLICY job_reviews_select_participant_or_public
  ON public.job_reviews FOR SELECT
  TO authenticated
  USING (
    reviewer_user_id = current_user_pk()
    OR reviewee_user_id = current_user_pk()
    OR is_public = true
  );

DROP POLICY IF EXISTS job_reviews_admin_all ON public.job_reviews;
CREATE POLICY job_reviews_admin_all
  ON public.job_reviews FOR ALL
  TO authenticated
  USING (public.is_admin())
  WITH CHECK (public.is_admin());

GRANT EXECUTE ON FUNCTION public.submit_job_review(integer, integer, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.submit_job_review(integer, integer, text) TO service_role;

-- ---------------------------------------------------------------------------
-- Public reviews list for provider profile (seeker -> provider reviews)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.list_public_reviews_for_user(
  p_user_id integer,
  p_limit integer DEFAULT 30
)
RETURNS TABLE (
  stars integer,
  review_text text,
  service_name text,
  created_at timestamptz,
  reviewer_first_name text
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    r.stars,
    COALESCE(r.review_text, '')::text AS review_text,
    s.name::text AS service_name,
    r.created_at,
    u.first_name::text AS reviewer_first_name
  FROM public.job_reviews r
  JOIN public.jobs j ON j.id = r.job_id
  JOIN public.quotes q ON q.id = j.quote_id
  JOIN public.services s ON s.id = q.service_id
  JOIN public.users u ON u.id = r.reviewer_user_id
  WHERE r.reviewee_user_id = p_user_id
    AND r.is_public = true
  ORDER BY r.created_at DESC
  LIMIT LEAST(GREATEST(COALESCE(p_limit, 30), 1), 100);
$$;

GRANT EXECUTE ON FUNCTION public.list_public_reviews_for_user(integer, integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.list_public_reviews_for_user(integer, integer) TO anon;

-- Aggregates for profile (view would be blocked by RLS on job_reviews for anon; use SECURITY DEFINER).
CREATE OR REPLACE FUNCTION public.get_public_rating_stats_for_user(p_user_id integer)
RETURNS TABLE (
  avg_stars numeric,
  review_count integer
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    coalesce(round(avg(r.stars)::numeric, 2), 0::numeric) AS avg_stars,
    count(*)::integer AS review_count
  FROM public.job_reviews r
  WHERE r.reviewee_user_id = p_user_id
    AND r.is_public = true;
$$;

GRANT EXECUTE ON FUNCTION public.get_public_rating_stats_for_user(integer) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_public_rating_stats_for_user(integer) TO anon;

-- ---------------------------------------------------------------------------
-- Refresh job list RPCs (drop + recreate with extended columns)
-- ---------------------------------------------------------------------------
DROP FUNCTION IF EXISTS public.get_pending_jobs();
CREATE OR REPLACE FUNCTION public.get_pending_jobs()
RETURNS TABLE (
  client text,
  provider text,
  "Service" text,
  "Price" double precision,
  location text,
  job_id integer,
  rated boolean,
  score real,
  my_review_submitted boolean,
  my_stars real,
  their_review_submitted boolean,
  their_stars real
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    (cli.first_name || ' ' || cli.last_name)::text AS client,
    (prov.first_name || ' ' || prov.last_name)::text AS provider,
    s.name AS "Service",
    j.price AS "Price",
    COALESCE(j.location, '')::text AS location,
    j.id AS job_id,
    false::boolean AS rated,
    0::real AS score,
    false::boolean AS my_review_submitted,
    0::real AS my_stars,
    false::boolean AS their_review_submitted,
    0::real AS their_stars
  FROM jobs j
  JOIN quotes q ON q.id = j.quote_id
  JOIN users cli ON cli.id = q.client_id
  JOIN users prov ON prov.id = q.provider_id
  JOIN services s ON s.id = q.service_id
  WHERE (q.client_id = current_user_pk() OR q.provider_id = current_user_pk())
    AND COALESCE(j.complete, false) = false;
$$;

DROP FUNCTION IF EXISTS public.get_completed_jobs();
CREATE OR REPLACE FUNCTION public.get_completed_jobs()
RETURNS TABLE (
  client text,
  provider text,
  "Service" text,
  "Price" double precision,
  location text,
  job_id integer,
  rated boolean,
  score real,
  my_review_submitted boolean,
  my_stars real,
  their_review_submitted boolean,
  their_stars real
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  WITH base AS (
    SELECT
      j.id AS jid,
      (cli.first_name || ' ' || cli.last_name)::text AS cname,
      (prov.first_name || ' ' || prov.last_name)::text AS pname,
      s.name AS sname,
      j.price AS jprice,
      COALESCE(j.location, '')::text AS jloc,
      q.client_id AS q_client,
      q.provider_id AS q_provider
    FROM jobs j
    JOIN quotes q ON q.id = j.quote_id
    JOIN users cli ON cli.id = q.client_id
    JOIN users prov ON prov.id = q.provider_id
    JOIN services s ON s.id = q.service_id
    WHERE (q.client_id = current_user_pk() OR q.provider_id = current_user_pk())
      AND COALESCE(j.complete, false) = true
  ),
  my_rev AS (
    SELECT r.job_id, r.stars::real AS st
    FROM job_reviews r
    WHERE r.reviewer_user_id = current_user_pk()
  ),
  their_rev AS (
    SELECT r.job_id, r.stars::real AS st
    FROM job_reviews r
    WHERE r.reviewee_user_id = current_user_pk()
  )
  SELECT
    b.cname AS client,
    b.pname AS provider,
    b.sname AS "Service",
    b.jprice AS "Price",
    b.jloc AS location,
    b.jid AS job_id,
    (mr.job_id IS NOT NULL) AS rated,
    COALESCE(mr.st, 0::real) AS score,
    (mr.job_id IS NOT NULL) AS my_review_submitted,
    COALESCE(mr.st, 0::real) AS my_stars,
    (tr.job_id IS NOT NULL) AS their_review_submitted,
    COALESCE(tr.st, 0::real) AS their_stars
  FROM base b
  LEFT JOIN my_rev mr ON mr.job_id = b.jid
  LEFT JOIN their_rev tr ON tr.job_id = b.jid;
$$;

-- their_stars visibility: private reviews have reviewee = client; only reviewee row exists in their_rev for that user when they are the client.
-- When current user is provider and client left private review toward client only — that row has reviewee = client, not provider, so tr is null for provider. Good.
-- When current user is client and provider left private review with reviewee = client, tr has row; is_public false; CASE returns stars (client sees).

COMMENT ON FUNCTION public.get_completed_jobs() IS 'Legacy rated/score = my review; my_stars/their_stars for bidirectional UI.';
