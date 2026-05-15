"""
Validate services.task_menu JSON for the ConnectHer mobile task menu parser.

See woman-global/supabase/migrations/20260513180000_services_task_menu.sql
and ServiceTaskMenuParser.kt (section | quantity | toggle).
"""
from __future__ import annotations

import json
import re
from typing import Any, Optional, Tuple

_ALLOWED_TYPES = frozenset({'section', 'quantity', 'toggle'})


def parse_and_validate_task_menu(raw: str) -> Tuple[Optional[dict], Optional[str]]:
    """
    Parse a JSON string into a PostgREST-ready dict for jsonb column task_menu.

    Returns (None, err) on failure.
    Returns (dict, None) on success with at least one quantity or toggle row.
    """
    text = (raw or '').strip()
    if not text:
        return None, 'Task menu JSON is empty.'
    try:
        data = json.loads(text)
    except json.JSONDecodeError as e:
        return None, f'Invalid JSON: {e}'

    if not isinstance(data, dict):
        return None, 'Task menu must be a JSON object (e.g. {"rows": [...]}).'

    rows = data.get('rows')
    if rows is None:
        return None, 'Missing required key "rows" (array).'
    if not isinstance(rows, list):
        return None, '"rows" must be an array.'

    out_rows: list[dict[str, Any]] = []
    billable = 0

    for i, row in enumerate(rows):
        if not isinstance(row, dict):
            return None, f'Row {i + 1}: must be an object.'
        rtype = str(row.get('type', '')).strip().lower()
        if rtype not in _ALLOWED_TYPES:
            return None, f'Row {i + 1}: type must be one of section, quantity, toggle (got {rtype!r}).'

        if rtype == 'section':
            title = str(row.get('title', '')).strip()
            if not title:
                return None, f'Row {i + 1} (section): "title" is required.'
            out: dict[str, Any] = {'type': 'section', 'title': title}
            st = row.get('subtitle')
            if st is not None and str(st).strip():
                out['subtitle'] = str(st).strip()
            out_rows.append(out)
            continue

        if rtype == 'quantity':
            key = str(row.get('key', '')).strip() or f'q_{i}'
            title = str(row.get('title', '')).strip() or key
            try:
                unit_price = float(row.get('unit_price', 0))
            except (TypeError, ValueError):
                return None, f'Row {i + 1} (quantity): "unit_price" must be a number.'
            if unit_price < 0:
                return None, f'Row {i + 1} (quantity): "unit_price" cannot be negative.'
            try:
                min_v = int(row.get('min', 0))
                max_v = int(row.get('max', 99))
                default_qty = int(row.get('default_qty', 0))
            except (TypeError, ValueError):
                return None, f'Row {i + 1} (quantity): min, max, default_qty must be integers.'
            min_v = max(0, min_v)
            max_v = max(min_v, max_v)
            default_qty = max(min_v, min(max_v, default_qty))
            out = {
                'type': 'quantity',
                'key': key,
                'title': title,
                'unit_label': str(row.get('unit_label', '') or ''),
                'unit_price': unit_price,
                'min': min_v,
                'max': max_v,
                'default_qty': default_qty,
            }
            img = row.get('image_url')
            if img is not None and str(img).strip():
                u = _sanitize_url(str(img).strip())
                if not u:
                    return None, f'Row {i + 1} (quantity): image_url must be an http(s) URL.'
                out['image_url'] = u
            out_rows.append(out)
            billable += 1
            continue

        # toggle
        key = str(row.get('key', '')).strip() or f't_{i}'
        title = str(row.get('title', '')).strip() or key
        try:
            unit_price = float(row.get('unit_price', 0))
        except (TypeError, ValueError):
            return None, f'Row {i + 1} (toggle): "unit_price" must be a number.'
        if unit_price < 0:
            return None, f'Row {i + 1} (toggle): "unit_price" cannot be negative.'
        default_checked = bool(row.get('default_checked', False))
        out = {
            'type': 'toggle',
            'key': key,
            'title': title,
            'unit_label': str(row.get('unit_label', '') or ''),
            'unit_price': unit_price,
            'default_checked': default_checked,
        }
        img = row.get('image_url')
        if img is not None and str(img).strip():
            u = _sanitize_url(str(img).strip())
            if not u:
                return None, f'Row {i + 1} (toggle): image_url must be an http(s) URL.'
            out['image_url'] = u
        out_rows.append(out)
        billable += 1

    if billable < 1:
        return None, 'Add at least one "quantity" or "toggle" row so the mobile app shows a task list.'

    banner = data.get('banner_image_url')
    result: dict[str, Any] = {'rows': out_rows}
    if banner is not None and str(banner).strip():
        b = _sanitize_url(str(banner).strip())
        if not b:
            return None, '"banner_image_url" must be an http(s) URL.'
        result['banner_image_url'] = b

    return result, None


def _sanitize_url(url: str) -> Optional[str]:
    s = (url or '').strip()
    if not s:
        return None
    if not re.match(r'^https?://', s, re.I):
        return None
    return s


def normalize_existing_task_menu(tm: Any) -> tuple[Optional[dict], str]:
    """Return (dict_or_none, json_string_for_textarea) for template / editor seed."""
    if tm is None:
        return None, '{\n  "rows": []\n}'
    if isinstance(tm, str):
        try:
            tm = json.loads(tm)
        except json.JSONDecodeError:
            return None, '{\n  "rows": []\n}'
    if not isinstance(tm, dict):
        return None, '{\n  "rows": []\n}'
    try:
        return tm, json.dumps(tm, indent=2, ensure_ascii=False)
    except (TypeError, ValueError):
        return None, '{\n  "rows": []\n}'
