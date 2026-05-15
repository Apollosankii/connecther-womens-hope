-- Default task menu for services that have none, so the Kotlin task UI is shown before auto-match.
-- Admins can override per row in `services.task_menu`.

UPDATE public.services
SET task_menu = '{
  "rows": [
    {
      "type": "quantity",
      "key": "units",
      "title": "Service units",
      "unit_label": "units",
      "unit_price": 500,
      "min": 1,
      "max": 99,
      "default_qty": 1,
      "image_url": null
    }
  ]
}'::jsonb
WHERE task_menu IS NULL
   OR task_menu = 'null'::jsonb
   OR task_menu = '{}'::jsonb;
