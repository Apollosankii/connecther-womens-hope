-- Optional per-service task menu for Kotlin/Expo "line item" booking UIs.
-- JSON shape (documented for admins):
-- {
--   "banner_image_url": "https://...",
--   "rows": [
--     { "type": "section", "title": "Home Cleaning", "subtitle": null },
--     { "type": "quantity", "key": "basic", "title": "Basic House Cleaning", "unit_label": "No of bedrooms",
--       "unit_price": 500, "min": 0, "max": 50, "default_qty": 0, "image_url": null },
--     { "type": "toggle", "key": "gear", "title": "Cleaning Equipment", "unit_label": "",
--       "unit_price": 650, "default_checked": false, "image_url": null }
--   ]
-- }
-- When null or empty object, apps fall back to manual price entry on the booking screen.

ALTER TABLE public.services
  ADD COLUMN IF NOT EXISTS task_menu jsonb DEFAULT NULL;

COMMENT ON COLUMN public.services.task_menu IS
  'Optional JSON menu: banner_image_url + rows (section | quantity | toggle). Used to prefill proposed_price and Quote: message.';
