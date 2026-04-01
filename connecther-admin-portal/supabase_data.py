"""
Admin Portal data layer: direct Supabase REST calls with user JWT.
Replaces FastAPI for admin operations. Requires RLS (run backend/sql/ migrations first).
"""
import hashlib
import os
import requests

def _gen_user_id(phone: str) -> str:
    return hashlib.sha1(phone.encode('utf-8')).hexdigest()[:6]


def _hash_password(password: str) -> str:
    try:
        import bcrypt
        return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
    except (ImportError, AttributeError, Exception):
        try:
            from passlib.context import CryptContext
            return CryptContext(schemes=['bcrypt'], deprecated='auto').hash(password)
        except (ImportError, AttributeError, Exception):
            return hashlib.sha256(password.encode()).hexdigest()

def _supabase_url() -> str:
    """Read on each use so values match after Flask loads .env (and shared woman-global/.env)."""
    return (os.environ.get('SUPABASE_URL') or '').strip().rstrip('/')


def _supabase_anon_key() -> str:
    return (os.environ.get('SUPABASE_ANON_KEY') or '').strip()


def _supabase_service_role_key() -> str:
    """Used only to sign private Storage URLs for admin document preview (optional)."""
    return (os.environ.get('SUPABASE_SERVICE_ROLE_KEY') or '').strip()


def _parse_bucket_and_path_from_storage_url(raw_url: str):
    """
    Extract (bucket_id, object_path) from a Supabase object URL, or (None, None).
    Handles /object/public/{bucket}/... and /object/{bucket}/... (authenticated).
    """
    if not raw_url or not isinstance(raw_url, str):
        return None, None
    u = raw_url.strip()
    for marker in ('/storage/v1/object/public/', '/storage/v1/object/sign/', '/storage/v1/object/'):
        idx = u.lower().find(marker.lower())
        if idx < 0:
            continue
        rest = u[idx + len(marker):].split('?', 1)[0].split('#', 1)[0]
        parts = rest.split('/', 1)
        if len(parts) == 2 and parts[0] and parts[1]:
            return parts[0], parts[1]
    return None, None


def _sign_storage_download_url(raw_url: str):
    """
    Return a time-limited signed URL for a private bucket object, or None.
    Requires SUPABASE_SERVICE_ROLE_KEY in connecther-admin-portal/.env (never expose in the browser).
    """
    base = _supabase_url()
    sk = _supabase_service_role_key()
    if not base or not sk or not raw_url.startswith('http'):
        return None
    bucket, path = _parse_bucket_and_path_from_storage_url(raw_url)
    if not bucket or not path:
        return None
    try:
        sign_endpoint = f"{base}/storage/v1/object/sign/{bucket}"
        headers = {
            'apikey': sk,
            'Authorization': f'Bearer {sk}',
            'Content-Type': 'application/json',
        }
        r = requests.post(
            sign_endpoint,
            headers=headers,
            json={'expiresIn': 7200, 'paths': [path]},
            timeout=20,
        )
        if r.status_code not in (200, 201):
            return None
        data = r.json()
        if isinstance(data, list) and data and isinstance(data[0], dict):
            signed = data[0].get('signedURL') or data[0].get('signedUrl')
            if signed and isinstance(signed, str):
                if signed.startswith('http'):
                    return signed
                if signed.startswith('/'):
                    return f"{base.rstrip('/')}{signed}"
        return None
    except Exception:
        return None


def _get_bearer(session):
    """Extract Bearer token from Flask session."""
    tok = session.get('token')
    if tok and isinstance(tok, dict):
        auth = tok.get('Authorization', '')
        if auth.startswith('Bearer '):
            return auth[7:].strip()
    return ''


def _req(session, method, path, params=None, json_data=None, timeout=10):
    """
    Make Supabase REST request with anon key + user JWT only.
    RLS policies (is_admin()) control access.
    """
    bearer = _get_bearer(session)
    base, key = _supabase_url(), _supabase_anon_key()
    if not bearer or not base or not key:
        return None
    url = f"{base}/rest/v1{path}"
    headers = {
        'apikey': key,
        'Authorization': f'Bearer {bearer}',
        'Content-Type': 'application/json',
        'Prefer': 'return=representation',
    }
    try:
        r = requests.request(
            method, url,
            params=params,
            json=json_data,
            headers=headers,
            timeout=timeout,
        )
        if r.status_code in (200, 201, 204):
            if not r.text:
                return []
            try:
                return r.json()
            except Exception:
                return []
        return None
    except Exception:
        return None


def _req_write(session, method, path, params=None, json_data=None, timeout=10):
    """
    Admin writes (PATCH/POST/DELETE) using user JWT + anon key.
    RLS admin policies (is_admin()) must allow the operation.
    """
    return _req(session, method, path, params=params, json_data=json_data, timeout=timeout)


def _patch_ok(res):
    """Return True if PATCH/update response indicates at least one row was affected."""
    if res is None:
        return False
    if isinstance(res, list):
        return len(res) > 0
    return isinstance(res, dict)


def _delete_rest_ok(session, path):
    """True if DELETE returned 200/204 (PostgREST often uses 204 with empty body)."""
    bearer = _get_bearer(session)
    base, key = _supabase_url(), _supabase_anon_key()
    if not bearer or not base or not key:
        return False
    url = f"{base}/rest/v1{path}"
    headers = {
        'apikey': key,
        'Authorization': f'Bearer {bearer}',
        'Prefer': 'return=minimal',
    }
    try:
        r = requests.delete(url, headers=headers, timeout=15)
        return r.status_code in (200, 204)
    except Exception:
        return False


def ensure_clerk_admin_in_table(token, email='', first_name='', last_name=''):
    """
    Ensure the Clerk user (from JWT) has a row in administrators.
    If not, insert one. Uses anon key + token; RLS admin_insert_self allows when clerk_user_id matches JWT sub.
    """
    base, key = _supabase_url(), _supabase_anon_key()
    if not token or not base or not key:
        return
    try:
        import base64
        import json
        parts = str(token).split('.')
        if len(parts) < 2:
            return
        payload = json.loads(base64.urlsafe_b64decode(parts[1] + '==').decode('utf-8'))
    except Exception:
        return
    sub = (payload.get('sub') or '').strip()
    if not sub:
        return
    url = f"{base}/rest/v1/administrators"
    headers = {
        'apikey': key,
        'Authorization': f'Bearer {token}',
        'Content-Type': 'application/json',
        'Prefer': 'return=minimal',
    }
    # Check if already exists
    try:
        r = requests.get(url, params={'clerk_user_id': f'eq.{sub}', 'select': 'id'}, headers=headers, timeout=10)
        if r.status_code == 200:
            data = r.json() if r.text else []
            if data and len(data) > 0:
                return
    except Exception:
        pass
    # Insert new admin into administrators table
    user_id = 'clerk_' + (sub[:12] if len(sub) > 12 else sub)
    row = {
        'clerk_user_id': sub,
        'email': (email or '').strip().lower() or (payload.get('email') or ''),
        'first_name': (first_name or '').strip() or (payload.get('first_name') or 'Admin'),
        'last_name': (last_name or '').strip() or (payload.get('last_name') or 'User'),
        'phone': '0000000000',
        'password': 'clerk',
        'user_id': user_id,
    }
    if not row['email']:
        row['email'] = f'{sub}@clerk.local'
    try:
        requests.post(url, json=row, headers=headers, timeout=10)
    except Exception:
        pass


def get_admin_profile(session):
    """Return current admin profile dict or None. RLS restricts to own row."""
    data = _req(session, 'GET', '/administrators')
    if not data or not isinstance(data, list) or len(data) == 0:
        return None
    row = data[0]
    name = ' '.join(filter(None, [
        (row.get('first_name') or '').strip(),
        (row.get('last_name') or '').strip(),
    ])).strip() or row.get('email') or row.get('user_id') or 'Admin'
    return {
        'user_id': row.get('user_id'),
        'email': row.get('email'),
        'first_name': row.get('first_name'),
        'last_name': row.get('last_name'),
        'phone': row.get('phone'),
        'name': name,
    }


def _user_to_snippet(row):
    """Map users row to provider_snippet shape. Includes id (internal users.id) when from get_pending_approvals."""
    if not isinstance(row, dict):
        return {'first_name': '', 'last_name': '', 'email': '', 'phone': '', 'nat_id': '', 'user_id': ''}
    out = {
        'first_name': row.get('first_name'),
        'last_name': row.get('last_name'),
        'WH_Badge': row.get('wh_badge') or row.get('WH_badge'),
        'email': row.get('email'),
        'phone': row.get('phone'),
        'nat_id': row.get('nat_id'),
        'user_id': row.get('user_id'),
    }
    if row.get('id') is not None:
        out['id'] = row['id']
    return out


def _user_to_profile(row):
    """Map users row to provider_profile shape."""
    return {
        'first_name': row.get('first_name'),
        'last_name': row.get('last_name'),
        'title': row.get('title'),
        'user_name': row.get('user_id'),
        'phone': row.get('phone'),
        'nat_id': row.get('nat_id'),
        'dob': row.get('birth_date'),
        'gender': row.get('gender'),
        'pic': row.get('prof_pic'),
        'WH Badge': row.get('wh_badge') or row.get('WH_badge'),
        'area_name': row.get('area_name'),
        'country': row.get('country'),
        'county': row.get('county'),
        'emm_cont_1': row.get('emm_cont_1'),
        'emm_cont_2': row.get('emm_cont_2'),
    }


def _list_users_via_rpc(session):
    """Fetch users via get_users_for_admin RPC (SECURITY DEFINER, bypasses RLS when is_admin)."""
    rpc = _req(session, 'POST', '/rpc/get_users_for_admin', json_data={})
    if rpc is not None and isinstance(rpc, list):
        return rpc
    return None


def list_providers(session):
    """List service providers. Uses get_users_for_admin RPC when REST returns empty."""
    rpc = _list_users_via_rpc(session)
    if rpc is not None:
        providers = [r for r in rpc if r.get('service_provider') is True]
        return [_user_to_snippet(r) for r in providers]
    data = _req(session, 'GET', '/users', params={'service_provider': 'is.true'})
    if not data or not isinstance(data, list):
        return []
    return [_user_to_snippet(r) for r in data]


def list_app_users(session):
    """List app users (non-providers). Uses get_users_for_admin RPC when REST returns empty."""
    rpc = _list_users_via_rpc(session)
    if rpc is not None:
        app_users = [r for r in rpc if r.get('service_provider') is not True]
        return [_user_to_snippet(r) for r in app_users]
    data = _req(session, 'GET', '/users')
    if not data or not isinstance(data, list):
        return []
    app_users = [r for r in data if r.get('service_provider') is not True]
    return [_user_to_snippet(r) for r in app_users]


def list_approvals(session):
    """
    List users pending provider approval. Ensures each row has 'id' (users.id) for the Review link.
    Tries RPC get_pending_approvals first; uses REST fallback when RPC rows lack id.
    """
    try:
        rpc = _req(session, 'POST', '/rpc/get_pending_approvals', json_data={})
        if rpc is not None and isinstance(rpc, list) and len(rpc) > 0:
            out = [_user_to_snippet(r) for r in rpc if isinstance(r, dict)]
            if out:
                return out
    except Exception:
        pass
    # Fallback: REST so we get users with id and user_id for the Review button
    try:
        data = _req(session, 'GET', '/users', params={
            'provider_application_pending': 'eq.true',
            'select': 'id,first_name,last_name,email,phone,nat_id,user_id',
        })
        if data and isinstance(data, list):
            return [_user_to_snippet(r) for r in data if isinstance(r, dict)]
    except Exception:
        pass
    return []


def get_user_internal_id(session, user_id_str):
    """Return public.users.id (PK) for the user with the given user_id (string). Used e.g. for notify-app-user."""
    data = _req(session, 'GET', '/users', params={'user_id': f'eq.{user_id_str}', 'select': 'id'})
    if not data or not isinstance(data, list) or len(data) == 0:
        return None
    return data[0].get('id')


def get_pending_application(session, user_id_str):
    """
    Return the pending provider_application row for this user (by user_id_str), or None.
    Used to show applicant's selected service_ids on the approve page.
    """
    data = _req(session, 'GET', '/provider_applications',
                params={'user_id_str': f'eq.{user_id_str}', 'status': 'eq.pending',
                        'select': 'id,user_id,user_id_str,service_ids,application_data'})
    if not data or not isinstance(data, list) or len(data) == 0:
        return None
    row = data[0]
    service_ids = row.get('service_ids')
    if service_ids is None:
        service_ids = []
    if not isinstance(service_ids, list):
        service_ids = list(service_ids) if service_ids else []
    row['service_ids'] = [int(x) for x in service_ids if x is not None]
    return row


def list_user_verification_documents(session, internal_user_id):
    """
    Documents uploaded for a user (e.g. provider application ID + certificates).
    Returns list of dicts: id, name (public URL), doc_type_name (when FK embed works).
    """
    if not internal_user_id:
        return []
    try:
        data = _req(session, 'GET', '/documents', params={
            'user_id': f'eq.{int(internal_user_id)}',
            'order': 'id.asc',
            'select': 'id,name,doc_type_id,document_type(name)',
        })
        if not data or not isinstance(data, list):
            return []
        out = []
        for r in data:
            if not isinstance(r, dict):
                continue
            url = r.get('name') or ''
            view_url = url
            if isinstance(url, str) and url.startswith('http'):
                signed = _sign_storage_download_url(url)
                if signed:
                    view_url = signed
            dt = r.get('document_type') or {}
            dt_name = dt.get('name') if isinstance(dt, dict) else None
            out.append({
                'id': r.get('id'),
                'name': view_url,
                'doc_type_name': dt_name or '',
                'doc_type_id': r.get('doc_type_id'),
            })
        if out and not any(d.get('doc_type_name') for d in out):
            types = _req(session, 'GET', '/document_type', params={'select': 'id,name'})
            if types and isinstance(types, list):
                id_to_name = {t.get('id'): (t.get('name') or '') for t in types if isinstance(t, dict)}
                for d in out:
                    tid = d.get('doc_type_id')
                    if tid is not None and not d.get('doc_type_name'):
                        d['doc_type_name'] = id_to_name.get(tid, '')
        return out
    except Exception:
        return []


def get_pending_application_by_internal_id(session, internal_id):
    """Return the pending provider_application row for this user (by users.id), or None."""
    data = _req(session, 'GET', '/provider_applications',
                params={'user_id': f'eq.{internal_id}', 'status': 'eq.pending',
                        'select': 'id,user_id,user_id_str,service_ids,application_data'})
    if not data or not isinstance(data, list) or len(data) == 0:
        return None
    row = data[0]
    service_ids = row.get('service_ids')
    if service_ids is None:
        service_ids = []
    if not isinstance(service_ids, list):
        service_ids = list(service_ids) if service_ids else []
    row['service_ids'] = [int(x) for x in service_ids if x is not None]
    return row


def get_provider(session, user_id):
    """Get provider profile by user_id (string)."""
    data = _req(session, 'GET', '/users', params={'user_id': f'eq.{user_id}'})
    if not data or not isinstance(data, list) or len(data) == 0:
        return None
    return _user_to_profile(data[0])


def get_provider_by_id(session, internal_id):
    """Get provider profile by users.id (internal integer)."""
    data = _req(session, 'GET', '/users', params={'id': f'eq.{internal_id}'})
    if not data or not isinstance(data, list) or len(data) == 0:
        return None
    return _user_to_profile(data[0])


def update_provider(session, payload):
    """Update provider. payload: first_name, last_name, phone, county, area_name, nat_id, emm_cont_1, emm_cont_2, user_id, title, subs."""
    user_id = payload.get('user_id')
    if not user_id:
        return False
    subs = payload.get('subs') or []
    update_data = {
        k: v for k, v in payload.items()
        if k in ('first_name', 'last_name', 'phone', 'county', 'area_name', 'nat_id', 'emm_cont_1', 'emm_cont_2', 'title')
        and v is not None
    }
    if update_data:
        res = _req_write(session, 'PATCH', f'/users?user_id=eq.{user_id}', json_data=update_data)
        if not _patch_ok(res):
            return False
    # Handle subscriptions: get user's internal id, replace subscriptions
    if subs:
        users = _req(session, 'GET', '/users', params={'user_id': f'eq.{user_id}', 'select': 'id'})
        if users and len(users) > 0:
            uid = users[0].get('id')
            if uid:
                _req_write(session, 'DELETE', '/subscriptions', params={'user_id': f'eq.{uid}'})
                for sid in subs:
                    try:
                        _req_write(session, 'POST', '/subscriptions', json_data={'user_id': uid, 'service_id': int(sid)})
                    except Exception:
                        pass
    return True


def approve_provider(session, user_id, service_ids=None):
    """Set provider_application_pending=false, service_provider=true. Update provider_applications status. Add subscriptions. Uses user_id string."""
    res = _req_write(session, 'PATCH', f'/users?user_id=eq.{user_id}',
               json_data={'service_provider': True, 'provider_application_pending': False})
    if not _patch_ok(res):
        return False
    from datetime import datetime, timezone
    reviewed_at = datetime.now(timezone.utc).isoformat()
    _req_write(session, 'PATCH', '/provider_applications',
               params={'user_id_str': f'eq.{user_id}', 'status': 'eq.pending'},
               json_data={'status': 'approved', 'reviewed_at': reviewed_at})
    if service_ids:
        users = _req(session, 'GET', '/users', params={'user_id': f'eq.{user_id}', 'select': 'id'})
        if users and len(users) > 0:
            uid = users[0].get('id')
            if uid:
                for sid in service_ids:
                    try:
                        _req_write(session, 'POST', '/subscriptions', json_data={'user_id': uid, 'service_id': int(sid)})
                    except Exception:
                        pass
    return True


def approve_provider_by_id(session, internal_id, service_ids=None):
    """
    Approve provider by users.id (internal integer). Ensures both users and provider_applications
    are updated so the provider no longer appears in pending list and gets subscriptions.
    """
    res = _req_write(session, 'PATCH', f'/users?id=eq.{internal_id}',
                     json_data={'service_provider': True, 'provider_application_pending': False})
    if not _patch_ok(res):
        return False
    from datetime import datetime, timezone
    reviewed_at = datetime.now(timezone.utc).isoformat()
    res_pa = _req_write(session, 'PATCH', '/provider_applications',
                        params={'user_id': f'eq.{internal_id}', 'status': 'eq.pending'},
                        json_data={'status': 'approved', 'reviewed_at': reviewed_at})
    if not _patch_ok(res_pa):
        pass  # User already updated; log in production if needed
    if service_ids:
        for sid in service_ids:
            try:
                _req_write(session, 'POST', '/subscriptions',
                           json_data={'user_id': internal_id, 'service_id': int(sid)})
            except Exception:
                pass
    return True


def _service_to_dict(row):
    """Map services row to asdict shape."""
    return {
        'service_id': row.get('id'),
        'name': row.get('name'),
        'description': row.get('description'),
        'min_price': row.get('min_price'),
        'pic': row.get('service_pic'),
    }


def list_services(session):
    """List services."""
    data = _req(session, 'GET', '/services')
    if not data or not isinstance(data, list):
        return []
    return [_service_to_dict(r) for r in data]


def get_service(session, service_id):
    """Get single service."""
    data = _req(session, 'GET', f'/services?id=eq.{service_id}')
    if not data or not isinstance(data, list) or len(data) == 0:
        return None
    return _service_to_dict(data[0])


def add_service(session, payload):
    """Add service. payload: name, description, min_price, service_pic (optional)."""
    res = _req_write(session, 'POST', '/services', json_data=payload)
    return res is not None and (isinstance(res, list) or isinstance(res, dict))


def update_service(session, service_id, payload):
    """Update service."""
    res = _req_write(session, 'PATCH', f'/services?id=eq.{service_id}', json_data=payload)
    return _patch_ok(res)


def delete_service(session, service_id):
    """
    Delete service by id. Prefer RPC admin_delete_service (cascades FK dependents);
    fall back to direct DELETE when the service has no references or RPC is not deployed.
    Returns (success: bool, error_message_or_none).
    """
    sid = int(service_id)
    rpc = _req_write(session, 'POST', '/rpc/admin_delete_service', json_data={'p_service_id': sid})
    if isinstance(rpc, list) and len(rpc) > 0:
        row = rpc[0]
        if row.get('ok') is True:
            return True, None
        err = row.get('err')
        if err == 'not_found':
            return False, 'Service not found.'
        if err == 'not_admin':
            return False, 'You do not have permission to delete services.'
        if err:
            return False, (err[:400] + '…') if len(str(err)) > 400 else str(err)
        return False, 'Could not delete service.'
    if _delete_rest_ok(session, f'/services?id=eq.{sid}'):
        return True, None
    return False, (
        'Could not delete this service. Apply sql/supabase_admin_delete_service.sql in the '
        'Supabase SQL editor, or remove subscriptions, bookings, and quotes that use it first.'
    )


def _emergency_hotline_to_dict(row):
    return {
        'id': row.get('id'),
        'name': row.get('name'),
        'description': row.get('description'),
        'phone': row.get('phone'),
        'sort_order': row.get('sort_order', 0),
        'is_active': row.get('is_active', True),
    }


def list_emergency_hotlines(session):
    """List all emergency hotlines (admin sees inactive too)."""
    data = _req(session, 'GET', '/emergency_hotlines')
    if not data or not isinstance(data, list):
        return []
    return sorted(
        [_emergency_hotline_to_dict(r) for r in data],
        key=lambda h: (h.get('sort_order', 0), h.get('id') or 0),
    )


def get_emergency_hotline(session, hotline_id):
    data = _req(session, 'GET', f'/emergency_hotlines?id=eq.{int(hotline_id)}')
    if not data or not isinstance(data, list) or len(data) == 0:
        return None
    return _emergency_hotline_to_dict(data[0])


def add_emergency_hotline(session, payload):
    row = {
        'name': (payload.get('name') or '').strip(),
        'description': (payload.get('description') or '').strip() or None,
        'phone': (payload.get('phone') or '').strip(),
        'sort_order': int(payload.get('sort_order', 0)),
        'is_active': bool(payload.get('is_active', True)),
    }
    if not row['name'] or not row['phone']:
        return False
    res = _req_write(session, 'POST', '/emergency_hotlines', json_data=row)
    return res is not None and (isinstance(res, list) or isinstance(res, dict))


def update_emergency_hotline(session, hotline_id, payload):
    allowed = ('name', 'description', 'phone', 'sort_order', 'is_active')
    row = {}
    for k, v in payload.items():
        if k not in allowed:
            continue
        if k == 'name' and v is not None:
            row[k] = str(v).strip()
        elif k == 'phone' and v is not None:
            row[k] = str(v).strip()
        elif k == 'description':
            row[k] = (str(v).strip() if v else None) or None
        else:
            row[k] = v
    if not row:
        return False
    res = _req_write(session, 'PATCH', f'/emergency_hotlines?id=eq.{int(hotline_id)}', json_data=row)
    return _patch_ok(res)


def delete_emergency_hotline(session, hotline_id):
    return _delete_rest_ok(session, f'/emergency_hotlines?id=eq.{int(hotline_id)}')


def list_document_types(session):
    """List document types for provider form."""
    data = _req(session, 'GET', '/document_type')
    if not data or not isinstance(data, list):
        return []
    return [{'docType': r.get('id'), 'name': r.get('name')} for r in data]


def _build_jobs(session, complete):
    """Fetch jobs with quote embed, resolve client/provider/service names."""
    path = f'/jobs?complete=is.{str(complete).lower()}'
    path += '&select=id,price,date,quote_id,quote:quotes(quote_code,client_id,provider_id,service_id)'
    data = _req(session, 'GET', path)
    if not data or not isinstance(data, list):
        return []
    out = []
    client_ids, provider_ids, service_ids = set(), set(), set()
    for j in data:
        q = j.get('quote') or {}
        if isinstance(q, dict):
            cid, pid, sid = q.get('client_id'), q.get('provider_id'), q.get('service_id')
            if cid:
                client_ids.add(cid)
            if pid:
                provider_ids.add(pid)
            if sid:
                service_ids.add(sid)
    users_by_id, services_by_id = {}, {}
    if client_ids or provider_ids:
        ids = list(client_ids | provider_ids)
        for i in range(0, len(ids), 100):
            chunk = ids[i:i + 100]
            users = _req(session, 'GET', '/users', params={'id': 'in.(' + ','.join(map(str, chunk)) + ')', 'select': 'id,first_name'})
            if users and isinstance(users, list):
                for u in users:
                    users_by_id[u['id']] = u.get('first_name') or ''
    if service_ids:
        ids = list(service_ids)
        for i in range(0, len(ids), 100):
            chunk = ids[i:i + 100]
            svc = _req(session, 'GET', '/services', params={'id': 'in.(' + ','.join(map(str, chunk)) + ')', 'select': 'id,name'})
            if svc and isinstance(svc, list):
                for s in svc:
                    services_by_id[s['id']] = s.get('name') or ''
    for j in data:
        q = j.get('quote') or {}
        if isinstance(q, dict):
            client = users_by_id.get(q.get('client_id'), '')
            provider = users_by_id.get(q.get('provider_id'), '')
            service = services_by_id.get(q.get('service_id'), '')
            out.append({
                'quote': q.get('quote_code'),
                'client': client,
                'provider': provider,
                'price': j.get('price'),
                'date': j.get('date'),
                'service': service,
            })
        else:
            out.append({'quote': '', 'client': '', 'provider': '', 'price': j.get('price'), 'date': j.get('date'), 'service': ''})
    return out


def all_pending_jobs(session):
    return _build_jobs(session, False)


def all_complete_jobs(session):
    return _build_jobs(session, True)


def search_providers(session, query):
    """Search providers by name. Uses get_users_for_admin when REST is blocked."""
    providers = list_providers(session)
    if not query or not str(query).strip():
        return providers
    q = str(query).strip().lower()
    return [p for p in providers
            if q in (p.get('first_name') or '').lower()
            or q in (p.get('last_name') or '').lower()
            or q in ((p.get('first_name') or '') + ' ' + (p.get('last_name') or '')).lower()]


def add_provider(session, kyc, documents, location, subs):
    """
    Add provider. kyc=dict, documents=list of {doc_type_id, name}, location=dict with lat/long, subs=list of service_ids.
    Returns (message, user_id).
    """
    phone = (kyc.get('phone') or '').strip()
    if not phone:
        return ('Phone required', '')
    user_id = _gen_user_id(phone)
    password_hash = _hash_password(phone)
    row = {
        'user_id': user_id,
        'first_name': kyc.get('first_name'),
        'last_name': kyc.get('last_name'),
        'phone': phone,
        'email': kyc.get('email') or None,
        'title': kyc.get('title') or 'Ms',
        'gender': kyc.get('gender') or 'M',
        'birth_date': kyc.get('birth_date'),
        'country': kyc.get('country'),
        'county': kyc.get('county'),
        'area_name': kyc.get('area_name'),
        'nat_id': kyc.get('nat_id'),
        'emm_cont_1': kyc.get('emm_cont_1'),
        'emm_cont_2': kyc.get('emm_cont_2'),
        'password': password_hash,
        'service_provider': True,
        'wh_badge': True,
        'provider_application_pending': False,
        'reg_platform': 'WHportal',
        'prof_pic': kyc.get('prof_pic'),
    }
    if row.get('birth_date'):
        from datetime import datetime
        try:
            if isinstance(row['birth_date'], str):
                row['birth_date'] = datetime.strptime(row['birth_date'], '%Y-%m-%d').date().isoformat()
        except Exception:
            row['birth_date'] = '1990-01-01'
    res = _req_write(session, 'POST', '/users', json_data=row)
    if res is None:
        return ('Failed to create user', '')
    created = res if isinstance(res, list) else [res]
    if not created:
        return ('Failed to create user', '')
    uid = created[0].get('id')
    if not uid:
        return ('User created but id missing', user_id)
    if documents:
        for d in documents:
            if d.get('doc_type_id') and d.get('name'):
                try:
                    _req_write(session, 'POST', '/documents', json_data={
                        'user_id': uid, 'doc_type_id': int(d['doc_type_id']),
                        'name': d['name'], 'verified': True,
                    })
                except Exception:
                    pass
    if location and location.get('latitude') is not None and location.get('longitude') is not None:
        try:
            _req_write(session, 'POST', '/live_location', json_data={'user_id': uid, 'latitude': float(location['latitude']), 'longitude': float(location['longitude'])})
        except Exception:
            pass
    if subs:
        for sid in subs:
            try:
                _req_write(session, 'POST', '/subscriptions', json_data={'user_id': uid, 'service_id': int(sid)})
            except Exception:
                pass
    return ('ok', user_id)


# =============================================================================
# Platform settings (free tier default connects)
# =============================================================================


def get_platform_settings(session):
    """Singleton platform_settings row id=1."""
    data = _req(session, 'GET', '/platform_settings', params={'id': 'eq.1'})
    if not data or not isinstance(data, list) or len(data) == 0:
        return {'id': 1, 'free_tier_connects': 5, 'updated_at': None}
    r = data[0]
    return {
        'id': r.get('id'),
        'free_tier_connects': int(r.get('free_tier_connects') or 0),
        'updated_at': r.get('updated_at'),
    }


def update_platform_settings(session, free_tier_connects):
    """Update default free-tier cap (new users / exploration)."""
    try:
        v = int(free_tier_connects)
    except (TypeError, ValueError):
        return False
    if v < 0:
        return False
    res = _req_write(session, 'PATCH', '/platform_settings?id=eq.1', json_data={'free_tier_connects': v})
    return _patch_ok(res)


def get_app_user_detail(session, internal_id):
    """
    App user row for admin support: identity + free_connects_*.
    internal_id is public.users.id (integer).
    """
    if internal_id is None:
        return None
    data = _req(session, 'GET', '/users', params={
        'id': f'eq.{int(internal_id)}',
        'select': 'id,first_name,last_name,phone,email,user_id,service_provider,free_connects_granted,free_connects_used',
    })
    if not data or not isinstance(data, list) or len(data) == 0:
        return None
    r = data[0]
    g = r.get('free_connects_granted')
    used = int(r.get('free_connects_used') or 0)
    remaining = None
    if g is not None:
        remaining = max(0, int(g) - used)
    ps = get_platform_settings(session)
    default_cap = int(ps.get('free_tier_connects') or 0)
    return {
        'id': r.get('id'),
        'first_name': r.get('first_name') or '',
        'last_name': r.get('last_name') or '',
        'phone': r.get('phone') or '',
        'email': r.get('email'),
        'user_id': r.get('user_id'),
        'service_provider': bool(r.get('service_provider')),
        'free_connects_granted': g,
        'free_connects_used': used,
        'free_connects_remaining': remaining,
        'platform_free_tier_default': default_cap,
    }


def patch_user_free_connects(session, internal_id, patch):
    """
    PATCH only keys present in patch: free_connects_granted (int or None), free_connects_used (int).
    free_connects_granted None clears the cap (platform default on first allocation).
    """
    if not patch:
        return False
    row = {}
    if 'free_connects_granted' in patch:
        v = patch['free_connects_granted']
        row['free_connects_granted'] = None if v is None else int(v)
    if 'free_connects_used' in patch:
        row['free_connects_used'] = max(0, int(patch['free_connects_used']))
    if not row:
        return False
    res = _req_write(session, 'PATCH', f'/users?id=eq.{int(internal_id)}', json_data=row)
    return _patch_ok(res)


def apply_platform_free_tier_to_all_users(session):
    """
    Batch: set free_connects_granted = platform_settings.free_tier_connects for ALL users
    where free_connects_granted IS NOT NULL (i.e. already allocated).
    """
    ps = get_platform_settings(session)
    cap = int(ps.get('free_tier_connects') or 0)
    res = _req_write(
        session, 'PATCH', '/users',
        params={'free_connects_granted': 'not.is.null'},
        json_data={'free_connects_granted': cap},
    )
    return _patch_ok(res)


# =============================================================================
# Booking requests (admin view)
# =============================================================================


def list_booking_requests(session, status_filter=None):
    """List booking requests for admin with user/service names."""
    path = '/booking_requests?select=id,client_id,provider_id,service_id,status,proposed_price,location_text,message,connect_consumed,connect_source,created_at,expires_at'
    params = {'order': 'created_at.desc'}
    if status_filter:
        params['status'] = f'eq.{status_filter}'
    data = _req(session, 'GET', path, params=params)
    if not data or not isinstance(data, list):
        return []
    user_ids = set()
    service_ids = set()
    for r in data:
        if r.get('client_id'):
            user_ids.add(r['client_id'])
        if r.get('provider_id'):
            user_ids.add(r['provider_id'])
        if r.get('service_id'):
            service_ids.add(r['service_id'])
    users_by_id = {}
    services_by_id = {}
    if user_ids:
        ids = ','.join(map(str, user_ids))
        users = _req(session, 'GET', '/users', params={'id': f'in.({ids})', 'select': 'id,first_name,last_name'})
        if users:
            for u in users:
                users_by_id[u['id']] = f"{u.get('first_name', '')} {u.get('last_name', '')}".strip()
    if service_ids:
        ids = ','.join(map(str, service_ids))
        svcs = _req(session, 'GET', '/services', params={'id': f'in.({ids})', 'select': 'id,name'})
        if svcs:
            for s in svcs:
                services_by_id[s['id']] = s.get('name', '')
    out = []
    for r in data:
        out.append({
            'id': r.get('id'),
            'client_name': users_by_id.get(r.get('client_id'), 'Unknown'),
            'provider_name': users_by_id.get(r.get('provider_id'), 'Unknown'),
            'service_name': services_by_id.get(r.get('service_id'), 'Unknown'),
            'status': r.get('status'),
            'proposed_price': r.get('proposed_price'),
            'location_text': r.get('location_text') or '—',
            'message': r.get('message') or '—',
            'connect_consumed': r.get('connect_consumed'),
            'connect_source': r.get('connect_source') or '—',
            'created_at': str(r.get('created_at', ''))[:19].replace('T', ' ') if r.get('created_at') else '—',
            'expires_at': str(r.get('expires_at', ''))[:19].replace('T', ' ') if r.get('expires_at') else '—',
        })
    return out


# =============================================================================
# Subscription plans (SUBSCRIPTION_ADMIN_PLAN)
# =============================================================================

def _plan_to_dict(row):
    """Map subscription_plans row to dict."""
    features = row.get('features')
    if isinstance(features, list):
        feats = features
    elif isinstance(features, str):
        try:
            import json
            feats = json.loads(features) if features else []
        except Exception:
            feats = []
    else:
        feats = []
    per = row.get('connects_per_period')
    try:
        per_int = int(per) if per is not None else None
    except (TypeError, ValueError):
        per_int = None
    return {
        'id': row.get('id'),
        'name': row.get('name'),
        'code': row.get('code'),
        'description': row.get('description'),
        'price': float(row.get('price') or 0),
        'currency': row.get('currency') or 'KES',
        'duration_type': row.get('duration_type') or 'month',
        'duration_value': int(row.get('duration_value') or 1),
        'features': feats,
        'is_active': bool(row.get('is_active', True)),
        'is_popular': bool(row.get('is_popular', False)),
        'sort_order': int(row.get('sort_order') or 0),
        'connects_limit_enabled': bool(row.get('connects_limit_enabled', False)),
        'connects_per_period': per_int,
        'connects_period_rule': (row.get('connects_period_rule') or 'subscription_term').strip() or 'subscription_term',
    }


def list_subscription_plans(session, active_only=False):
    """List subscription plans."""
    params = {}
    if active_only:
        params['is_active'] = 'is.true'
    data = _req(session, 'GET', '/subscription_plans', params=params or None)
    if not data or not isinstance(data, list):
        return []
    return sorted([_plan_to_dict(r) for r in data], key=lambda p: p['sort_order'])


def get_subscription_plan(session, plan_id):
    """Get single subscription plan."""
    data = _req(session, 'GET', f'/subscription_plans?id=eq.{plan_id}')
    if not data or not isinstance(data, list) or len(data) == 0:
        return None
    return _plan_to_dict(data[0])


def add_subscription_plan(session, payload):
    """Create subscription plan. payload includes connects_limit_enabled, connects_per_period, connects_period_rule."""
    per_raw = payload.get('connects_per_period')
    try:
        per_val = int(per_raw) if per_raw is not None and str(per_raw).strip() != '' else None
    except (TypeError, ValueError):
        per_val = None
    rule = (payload.get('connects_period_rule') or 'subscription_term').strip() or 'subscription_term'
    row = {
        'name': payload.get('name'),
        'code': payload.get('code') or (payload.get('name', '').lower().replace(' ', '_') if payload.get('name') else None),
        'description': payload.get('description') or None,
        'price': float(payload.get('price', 0)),
        'currency': payload.get('currency') or 'KES',
        'duration_type': payload.get('duration_type') or 'month',
        'duration_value': int(payload.get('duration_value', 1)),
        'features': payload.get('features') if isinstance(payload.get('features'), (list, type(None))) else [],
        'is_active': bool(payload.get('is_active', True)),
        'is_popular': bool(payload.get('is_popular', False)),
        'sort_order': int(payload.get('sort_order', 0)),
        'connects_limit_enabled': bool(payload.get('connects_limit_enabled', False)),
        'connects_per_period': per_val,
        'connects_period_rule': rule,
    }
    res = _req_write(session, 'POST', '/subscription_plans', json_data=row)
    return res is not None and (isinstance(res, list) or isinstance(res, dict))


def update_subscription_plan(session, plan_id, payload):
    """Update subscription plan."""
    allowed = (
        'name', 'code', 'description', 'price', 'currency', 'duration_type', 'duration_value',
        'features', 'is_active', 'is_popular', 'sort_order',
        'connects_limit_enabled', 'connects_per_period', 'connects_period_rule',
    )
    row = {}
    for k in allowed:
        if k not in payload:
            continue
        v = payload[k]
        if k == 'connects_per_period':
            if v is None or (isinstance(v, str) and not str(v).strip()):
                row[k] = None
            else:
                row[k] = int(v)
        elif k == 'connects_limit_enabled':
            row[k] = bool(v)
        elif k == 'connects_period_rule' and v is not None:
            row[k] = str(v).strip() or 'subscription_term'
        elif v is not None:
            row[k] = v
    if not row:
        return False
    res = _req_write(session, 'PATCH', f'/subscription_plans?id=eq.{plan_id}', json_data=row)
    return _patch_ok(res)


def delete_subscription_plan(session, plan_id):
    """Soft delete: set is_active=false. Hard delete if no active user subscriptions."""
    # Check for active user subscriptions
    subs = _req(session, 'GET', '/user_plan_subscriptions', params={'plan_id': f'eq.{plan_id}', 'status': 'eq.active'})
    if subs and len(subs) > 0:
        res = _req_write(session, 'PATCH', f'/subscription_plans?id=eq.{plan_id}', json_data={'is_active': False})
        return _patch_ok(res)
    res = _req_write(session, 'DELETE', f'/subscription_plans?id=eq.{plan_id}')
    return _patch_ok(res)


def list_users_for_plan_grant(session):
    """List users with id for grant dropdown (app users + providers)."""
    rpc = _req(session, 'POST', '/rpc/get_users_for_admin', json_data={})
    if rpc is not None and isinstance(rpc, list) and rpc:
        return [{'id': r.get('id'), 'first_name': r.get('first_name'), 'last_name': r.get('last_name'), 'phone': r.get('phone'), 'email': r.get('email'), 'user_id': r.get('user_id')} for r in rpc]
    data = _req(session, 'GET', '/users', params={'select': 'id,first_name,last_name,phone,email,user_id'})
    if not data or not isinstance(data, list):
        return []
    return [{'id': r.get('id'), 'first_name': r.get('first_name'), 'last_name': r.get('last_name'), 'phone': r.get('phone'), 'email': r.get('email'), 'user_id': r.get('user_id')} for r in data]


def list_user_plan_subscriptions(session, status_filter=None, plan_filter=None):
    """List user plan subscriptions with user and plan names. status_filter, plan_filter optional."""
    path = (
        '/user_plan_subscriptions?select=id,user_id,plan_id,status,started_at,expires_at,cancelled_at,'
        'payment_reference,notes,connects_granted,connects_used,connects_period_started_at'
    )
    params = {}
    if status_filter:
        params['status'] = f'eq.{status_filter}'
    if plan_filter:
        params['plan_id'] = f'eq.{plan_filter}'
    data = _req(session, 'GET', path, params=params if params else None)
    if not data or not isinstance(data, list):
        return []
    out = []
    user_ids = set(r.get('user_id') for r in data if r.get('user_id'))
    plan_ids = set(r.get('plan_id') for r in data if r.get('plan_id'))
    users_by_id = {}
    plans_by_id = {}
    if user_ids:
        ids = ','.join(map(str, user_ids))
        users = _req(session, 'GET', '/users', params={'id': f'in.({ids})', 'select': 'id,first_name,last_name,phone,email'})
        if users:
            for u in users:
                users_by_id[u['id']] = u
    if plan_ids:
        ids = ','.join(map(str, plan_ids))
        plans = _req(session, 'GET', '/subscription_plans', params={
            'id': f'in.({ids})',
            'select': 'id,name,connects_limit_enabled,connects_per_period,connects_period_rule',
        })
        if plans:
            for p in plans:
                plans_by_id[p['id']] = p
    for r in data:
        u = users_by_id.get(r.get('user_id')) or {}
        p = plans_by_id.get(r.get('plan_id')) or {}
        grant = r.get('connects_granted')
        used = int(r.get('connects_used') or 0)
        limit_on = bool(p.get('connects_limit_enabled'))
        if not limit_on or grant is None:
            connects_label = 'Unlimited'
            connects_remaining = None
        else:
            g = int(grant)
            connects_remaining = max(0, g - used)
            connects_label = f'{connects_remaining} / {g}'
        out.append({
            'id': r.get('id'),
            'user_id': r.get('user_id'),
            'plan_id': r.get('plan_id'),
            'user_name': f"{u.get('first_name', '')} {u.get('last_name', '')}".strip() or 'Unknown',
            'user_phone': u.get('phone') or '—',
            'user_email': u.get('email') or '—',
            'plan_name': p.get('name') or 'Unknown',
            'status': r.get('status') or 'active',
            'started_at': str(r.get('started_at', ''))[:10] if r.get('started_at') else '—',
            'expires_at': str(r.get('expires_at', ''))[:10] if r.get('expires_at') else '—',
            'payment_reference': r.get('payment_reference') or '—',
            'notes': r.get('notes') or '',
            'connects_granted': grant,
            'connects_used': used,
            'connects_period_started_at': str(r.get('connects_period_started_at', ''))[:10] if r.get('connects_period_started_at') else '—',
            'connects_label': connects_label,
            'connects_remaining': connects_remaining,
            'plan_connects_limit_enabled': limit_on,
            'plan_connects_period_rule': p.get('connects_period_rule') or 'subscription_term',
        })
    return out


def list_recent_paystack_transactions(session, limit=40):
    """Recent Paystack checkout rows for revenue / funnel tracking (admin RLS)."""
    data = _req(session, 'GET', '/paystack_transactions', params={
        'select': 'id,user_id,plan_id,reference,status,amount_kobo,currency,email,created_at',
        'order': 'created_at.desc',
        'limit': str(limit),
    })
    if not data or not isinstance(data, list):
        return []
    user_ids = set(r.get('user_id') for r in data if r.get('user_id'))
    plan_ids = set(r.get('plan_id') for r in data if r.get('plan_id'))
    users_by_id = {}
    plans_by_id = {}
    if user_ids:
        ids = ','.join(map(str, user_ids))
        users = _req(session, 'GET', '/users', params={
            'id': f'in.({ids})',
            'select': 'id,first_name,last_name,phone,email',
        })
        if users:
            for u in users:
                users_by_id[u['id']] = u
    if plan_ids:
        ids = ','.join(map(str, plan_ids))
        plans = _req(session, 'GET', '/subscription_plans', params={
            'id': f'in.({ids})',
            'select': 'id,name',
        })
        if plans:
            for p in plans:
                plans_by_id[p['id']] = p
    out = []
    for r in data:
        u = users_by_id.get(r.get('user_id')) or {}
        p = plans_by_id.get(r.get('plan_id')) or {}
        out.append({
            'id': r.get('id'),
            'reference': r.get('reference'),
            'status': r.get('status'),
            'amount_kobo': r.get('amount_kobo'),
            'currency': r.get('currency') or 'KES',
            'created_at': r.get('created_at'),
            'user_label': f"{u.get('first_name', '')} {u.get('last_name', '')}".strip() or 'Unknown',
            'user_email': u.get('email') or r.get('email'),
            'user_phone': u.get('phone'),
            'plan_name': p.get('name') or '—',
        })
    return out


def subscription_tracking_dashboard(session):
    """Subscriber KPIs, connects snapshot, and recent payments — all from Supabase REST."""
    subs = _req(session, 'GET', '/user_plan_subscriptions', params={
        'select': 'id,status,plan_id,connects_granted,connects_used,user_id',
    })
    subs = subs if isinstance(subs, list) else []
    by_status = {}
    connects_snapshot = []  # active rows with caps
    for r in subs:
        st = r.get('status') or 'unknown'
        by_status[st] = by_status.get(st, 0) + 1
        if st == 'active' and r.get('connects_granted') is not None:
            uid = r.get('user_id')
            g = int(r.get('connects_granted') or 0)
            used = int(r.get('connects_used') or 0)
            connects_snapshot.append({
                'subscription_id': r.get('id'),
                'user_id': uid,
                'plan_id': r.get('plan_id'),
                'remaining': max(0, g - used),
                'granted': g,
                'used': used,
            })
    plans = list_subscription_plans(session)
    plan_names = {p['id']: p.get('name') for p in plans}
    by_plan = {}
    for r in subs:
        pid = r.get('plan_id')
        if pid is None:
            continue
        by_plan[pid] = by_plan.get(pid, 0) + 1
    by_plan_rows = [
        {
            'plan_id': pid,
            'plan_name': plan_names.get(pid) or f'Plan #{pid}',
            'count': cnt,
        }
        for pid, cnt in sorted(by_plan.items(), key=lambda x: -x[1])
    ]
    recent = list_recent_paystack_transactions(session, limit=40)
    user_ids_snap = list(set(x['user_id'] for x in connects_snapshot if x.get('user_id')))
    snap_users = {}
    if user_ids_snap:
        ids = ','.join(map(str, user_ids_snap))
        urows = _req(session, 'GET', '/users', params={
            'id': f'in.({ids})',
            'select': 'id,first_name,last_name,phone,email',
        })
        if urows:
            for u in urows:
                snap_users[u['id']] = u
    for row in connects_snapshot:
        u = snap_users.get(row.get('user_id')) or {}
        row['user_label'] = f"{u.get('first_name', '')} {u.get('last_name', '')}".strip() or 'Unknown'
        row['plan_name'] = plan_names.get(row.get('plan_id')) or '—'
    connects_snapshot.sort(key=lambda x: -(x.get('remaining') or 0))

    return {
        'total_sub_rows': len(subs),
        'by_status': by_status,
        'by_plan_rows': by_plan_rows,
        'connects_snapshot': connects_snapshot[:80],
        'recent_paystack': recent,
        'plans': plans,
    }


def _connect_fields_for_new_subscription(plan_dict, started_at):
    """Match paystack finalize: set connects on new subscription when plan caps connects."""
    if not plan_dict:
        return {}
    limit_on = plan_dict.get('connects_limit_enabled') is True
    per_raw = plan_dict.get('connects_per_period')
    try:
        per_num = int(per_raw) if per_raw is not None else None
    except (TypeError, ValueError):
        per_num = None
    if limit_on and per_num is not None and per_num >= 0:
        started = str(started_at)[:10]
        return {
            'connects_granted': per_num,
            'connects_used': 0,
            'connects_period_started_at': started,
        }
    return {}


def grant_user_plan_subscription(session, user_id, plan_id, started_at, expires_at, payment_reference=None, notes=None):
    """Grant a plan to a user. user_id=users.id (integer), plan_id=subscription_plans.id."""
    plan = get_subscription_plan(session, plan_id)
    row = {
        'user_id': int(user_id),
        'plan_id': int(plan_id),
        'status': 'active',
        'started_at': started_at,
        'expires_at': expires_at,
        'payment_reference': payment_reference or None,
        'notes': notes or None,
    }
    row.update(_connect_fields_for_new_subscription(plan, started_at))
    res = _req_write(session, 'POST', '/user_plan_subscriptions', json_data=row)
    return res is not None and (isinstance(res, list) or isinstance(res, dict))


def get_user_plan_subscription(session, sub_id):
    """Get one user plan subscription by id."""
    data = _req(session, 'GET', f'/user_plan_subscriptions?id=eq.{sub_id}')
    if not data or not isinstance(data, list) or len(data) == 0:
        return None
    r = data[0]
    users = _req(session, 'GET', '/users', params={'id': f'eq.{r.get("user_id")}', 'select': 'id,first_name,last_name,phone,email'})
    plans = _req(session, 'GET', '/subscription_plans', params={
        'id': f'eq.{r.get("plan_id")}',
        'select': 'id,name,price,currency,duration_type,duration_value,connects_limit_enabled,connects_per_period,connects_period_rule',
    })
    u = users[0] if users else {}
    p = plans[0] if plans else {}
    cancelled = r.get('cancelled_at')
    cg = r.get('connects_granted')
    cu = int(r.get('connects_used') or 0)
    limit_on = bool(p.get('connects_limit_enabled'))
    if not limit_on or cg is None:
        connects_remaining = None
        connects_unlimited = True
    else:
        connects_remaining = max(0, int(cg) - cu)
        connects_unlimited = False
    return {
        'id': r.get('id'),
        'user_id': r.get('user_id'),
        'plan_id': r.get('plan_id'),
        'user_name': f"{u.get('first_name', '')} {u.get('last_name', '')}".strip(),
        'user_phone': u.get('phone'),
        'user_email': u.get('email'),
        'plan_name': p.get('name'),
        'plan_price': p.get('price'),
        'plan_currency': p.get('currency'),
        'plan_connects_limit_enabled': limit_on,
        'plan_connects_per_period': p.get('connects_per_period'),
        'plan_connects_period_rule': (p.get('connects_period_rule') or 'subscription_term'),
        'status': r.get('status'),
        'started_at': r.get('started_at'),
        'expires_at': r.get('expires_at'),
        'cancelled_at': cancelled,
        'payment_reference': r.get('payment_reference'),
        'notes': r.get('notes'),
        'connects_granted': cg,
        'connects_used': cu,
        'connects_period_started_at': r.get('connects_period_started_at'),
        'connects_remaining': connects_remaining,
        'connects_unlimited': connects_unlimited,
    }


def cancel_user_plan_subscription(session, sub_id):
    """Cancel subscription: set status=cancelled, cancelled_at=today."""
    from datetime import date
    row = {'status': 'cancelled', 'cancelled_at': date.today().isoformat()}
    res = _req_write(session, 'PATCH', f'/user_plan_subscriptions?id=eq.{sub_id}', json_data=row)
    return _patch_ok(res)


def extend_user_plan_subscription(session, sub_id, new_expires_at):
    """Extend subscription to new_expires_at."""
    row = {'expires_at': new_expires_at}
    res = _req_write(session, 'PATCH', f'/user_plan_subscriptions?id=eq.{sub_id}', json_data=row)
    return _patch_ok(res)


def update_user_plan_subscription(session, sub_id, payload):
    """Update subscription metadata and optional connect balances (admin support)."""
    allowed = (
        'status', 'expires_at', 'cancelled_at', 'payment_reference', 'notes',
        'connects_granted', 'connects_used', 'connects_period_started_at',
    )
    row = {}
    for k in allowed:
        if k not in payload:
            continue
        v = payload[k]
        if k == 'connects_granted':
            if v is None or v == '':
                row[k] = None
            else:
                row[k] = int(v)
        elif k == 'connects_used':
            if v is None or v == '':
                continue
            row[k] = max(0, int(v))
        elif k == 'connects_period_started_at':
            if v:
                row[k] = str(v)[:10]
        elif v is not None and v != '':
            row[k] = v
    if not row:
        return False
    res = _req_write(session, 'PATCH', f'/user_plan_subscriptions?id=eq.{sub_id}', json_data=row)
    return _patch_ok(res)
