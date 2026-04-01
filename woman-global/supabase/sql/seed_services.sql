-- Seed default services for ConnectHer (run in Supabase SQL Editor if services table is empty)
-- Aligns with Android app fallback and Admin Portal MOCK_SERVICES

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.services LIMIT 1) THEN
    INSERT INTO public.services (name, description, min_price, service_pic)
    VALUES
      ('Mama Fua', 'Professional laundry and ironing services', 500.0, NULL),
      ('Tailor', 'Custom tailoring and alterations', 800.0, NULL),
      ('Care Giver', 'Elderly and child care support', 1200.0, NULL),
      ('House Manager', 'Household management and supervision', 1500.0, NULL),
      ('Errand Girl', 'Run errands and general assistance', 600.0, NULL);
  END IF;
END $$;
