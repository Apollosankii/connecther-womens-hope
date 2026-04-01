-- Nearby providers using PostGIS geography (meters). When lat/lng omitted, delegates to get_providers_for_service (all providers for service).

CREATE OR REPLACE FUNCTION public.get_providers_for_service_near(
  p_service_id integer,
  p_lat double precision DEFAULT NULL,
  p_lng double precision DEFAULT NULL,
  p_radius_meters double precision DEFAULT 10000
)
RETURNS SETOF jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF p_lat IS NULL OR p_lng IS NULL THEN
    RETURN QUERY SELECT * FROM public.get_providers_for_service(p_service_id);
    RETURN;
  END IF;

  IF p_radius_meters IS NULL OR p_radius_meters <= 0 THEN
    p_radius_meters := 10000;
  END IF;

  RETURN QUERY
  SELECT jsonb_build_object(
    'first_name', u.first_name,
    'last_name', u.last_name,
    'title', u.title,
    'user_name', u.user_id,
    'phone', u.phone,
    'nat_id', u.nat_id,
    'dob', u.birth_date,
    'gender', u.gender,
    'pic', u.prof_pic,
    'WH Badge', u."WH_badge",
    'area_name', u.area_name,
    'country', u.country,
    'county', u.county,
    'emm_cont_1', u.emm_cont_1,
    'emm_cont_2', u.emm_cont_2,
    'id', u.id,
    'latitude', ll.latitude,
    'longitude', ll.longitude,
    'working_hours', u.working_hours,
    'occupation', u.occupation
  )
  FROM public.users u
  JOIN public.subscriptions s ON s.user_id = u.id AND s.service_id = p_service_id
  INNER JOIN LATERAL (
    SELECT ll.latitude, ll.longitude
    FROM public.live_location ll
    WHERE ll.user_id = u.id
    ORDER BY ll.id DESC
    LIMIT 1
  ) ll ON TRUE
  WHERE u.service_provider = true
    AND COALESCE(u.available_for_booking, true) = true
    AND NOT public.provider_has_incomplete_job(u.id)
    AND ll.latitude IS NOT NULL
    AND ll.longitude IS NOT NULL
    AND ST_DWithin(
      ST_SetSRID(ST_MakePoint(ll.longitude, ll.latitude), 4326)::geography,
      ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::geography,
      p_radius_meters
    );
END;
$$;

GRANT EXECUTE ON FUNCTION public.get_providers_for_service_near(integer, double precision, double precision, double precision) TO anon;
GRANT EXECUTE ON FUNCTION public.get_providers_for_service_near(integer, double precision, double precision, double precision) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_providers_for_service_near(integer, double precision, double precision, double precision) TO service_role;
