"""
ConnectHer Admin Portal – Flask app with direct Supabase connection.
No FastAPI required. Run supabase_rls_admin.sql in Supabase before use.
"""
from flask import Flask, url_for, redirect, render_template, request, session, make_response, jsonify, flash, send_from_directory, abort
import logging
import mimetypes
import os
import uuid
import base64

logger = logging.getLogger(__name__)

# Load .env before reading config (later files override earlier for duplicate keys):
# 1) woman-global/.env — shared Supabase credentials (same as Android app)
# 2) connecther-admin-portal/.env — local secrets (FLASK_*, CLERK_*, etc.)
def _load_env():
    try:
        from dotenv import load_dotenv
    except ImportError:
        return
    _dir = os.path.dirname(os.path.abspath(__file__))
    repo_root = os.path.normpath(os.path.join(_dir, '..'))
    shared_env = os.path.join(repo_root, 'woman-global', '.env')
    portal_env = os.path.join(_dir, '.env')
    if os.path.isfile(shared_env):
        load_dotenv(shared_env, override=False)
    if os.path.isfile(portal_env):
        load_dotenv(portal_env, override=True)
    elif not os.path.isfile(portal_env):
        load_dotenv(override=False)

_load_env()

_ROOT = os.path.dirname(os.path.abspath(__file__))

app = Flask(__name__,
    template_folder=os.path.join(_ROOT, 'templates'),
    static_folder=os.path.join(_ROOT, 'static'))

app.secret_key = os.environ.get('FLASK_SECRET_KEY', '_5#y2L"F4Q8z\n\xec]/')

if os.environ.get('FLASK_DEBUG') == '1':
    app.config['DEBUG'] = True
    app.config['PROPAGATE_EXCEPTIONS'] = True

app.config['TEMPLATES_AUTO_RELOAD'] = True
app.config['SESSION_COOKIE_SAMESITE'] = 'Lax'
app.config['SESSION_COOKIE_HTTPONLY'] = True


@app.template_filter('ch_service_image_url')
def _ch_service_image_url(pic):
    """Resolve services.service_pic to a URL (Supabase public URL or legacy local file)."""
    if not pic:
        return ''
    s = str(pic).strip()
    if s.startswith('http://') or s.startswith('https://'):
        return s
    return url_for('legacy_service_local_image', filename=os.path.basename(s))


SUPABASE_URL = (os.environ.get('SUPABASE_URL') or '').strip().rstrip('/')
SUPABASE_ANON_KEY = (os.environ.get('SUPABASE_ANON_KEY') or '').strip()
CLERK_PUBLISHABLE_KEY = (os.environ.get('CLERK_PUBLISHABLE_KEY') or '').strip()

_supabase_host = (SUPABASE_URL.split('//')[-1].split('/')[0] if SUPABASE_URL else '(missing)')
if not SUPABASE_URL or not SUPABASE_ANON_KEY:
    logger.warning(
        'Supabase not configured: set SUPABASE_URL and SUPABASE_ANON_KEY in '
        'woman-global/.env and/or connecther-admin-portal/.env'
    )
else:
    logger.info(
        'Supabase: PostgREST host=%s anon_key_loaded=%s',
        _supabase_host,
        bool(SUPABASE_ANON_KEY) and len(SUPABASE_ANON_KEY) > 20,
    )
if os.environ.get('FLASK_DEBUG') == '1':
    logger.info(
        'Supabase env (debug): configured=%s url_host=%s',
        bool(SUPABASE_URL and SUPABASE_ANON_KEY),
        _supabase_host,
    )


def _auth_headers():
    tok = session.get('token')
    if tok and isinstance(tok, dict) and tok.get('Authorization'):
        return tok
    return None


def _require_auth():
    if not _auth_headers():
        return redirect(url_for('login'))
    return None


def _current_admin():
    """Fetch admin profile from Supabase. Returns dict or None."""
    import supabase_data
    return supabase_data.get_admin_profile(session)


@app.context_processor
def inject_common():
    """Inject name for base template so no view forgets. Must never raise."""
    try:
        if _auth_headers():
            user = _current_admin()
            if user and isinstance(user, dict):
                n = user.get('name') or user.get('email') or 'Admin'
                return {'name': n}
    except Exception:
        pass
    return {'name': 'Admin'}


@app.errorhandler(500)
def handle_500(err):
    logger.exception("Server error (500): %s", err)
    if app.config.get('DEBUG'):
        raise err
    return (
        '<h1>Server error (500)</h1><p>Set <code>FLASK_DEBUG=1</code> and restart to see details.</p>',
        500,
    )

_NO_CACHE_HEADERS = {
    'Cache-Control': 'no-store, no-cache, must-revalidate, max-age=0',
    'Pragma': 'no-cache',
    'Expires': '0',
}


@app.route('/', methods=['GET'])
def home():
    if _auth_headers():
        r = redirect(url_for('dash'))
    else:
        r = redirect(url_for('login'))
    for k, v in _NO_CACHE_HEADERS.items():
        r.headers[k] = v
    return r


@app.route('/login', methods=['GET'])
def login():
    supabase_ok = bool(SUPABASE_URL and SUPABASE_ANON_KEY)
    clerk_ok = bool(CLERK_PUBLISHABLE_KEY and not CLERK_PUBLISHABLE_KEY.startswith('pk_test_xxxxx'))
    resp = make_response(render_template('login_supabase.html',
        supabase_url=SUPABASE_URL,
        supabase_anon_key=SUPABASE_ANON_KEY,
        supabase_configured=supabase_ok,
        clerk_publishable_key=CLERK_PUBLISHABLE_KEY,
        clerk_configured=clerk_ok))
    for k, v in _NO_CACHE_HEADERS.items():
        resp.headers[k] = v
    return resp


@app.route('/auth/ok', methods=['GET'])
def auth_ok():
    return jsonify({'ok': True, 'version': 'supabase-direct-v1'}), 200


@app.route('/auth/supabase/callback', methods=['POST'])
def auth_supabase_callback():
    """Store Supabase JWT in session and redirect to dashboard. Anon key only."""
    want_json = request.is_json or 'application/json' in (request.headers.get('Accept') or '')
    def json_err(msg, code=400):
        if want_json:
            return jsonify({'ok': False, 'detail': msg}), code
        return None
    try:
        token = None
        if request.form:
            token = request.form.get('access_token')
        if not token:
            try:
                data = request.get_json(silent=True) or {}
                token = data.get('access_token')
            except Exception:
                pass
        if not token or not str(token).strip():
            out = json_err('Missing token', 400)
            if out:
                return out
            flash('Missing token. Please try signing in again.')
            return redirect(url_for('login'))
        token = str(token).strip()
        session.clear()
        session['token'] = {'Authorization': 'Bearer ' + token}
        session.modified = True
        if want_json:
            return jsonify({'ok': True, 'redirect': url_for('dash')}), 200
        return redirect(url_for('dash'))
    except Exception as e:
        if want_json:
            return jsonify({'ok': False, 'detail': 'Sign-in error'}), 500
        flash('Sign-in error. Please try again.')
        return redirect(url_for('login'))


@app.route('/auth/admin-signup', methods=['POST'])
def auth_admin_signup():
    """
    Register new admin: insert into administrators table using anon key + user JWT only.
    RLS policy admin_insert_self allows INSERT when JWT email matches row email.
    Supabase Auth "Confirm email" must be OFF so signUp returns a session and we have a JWT.
    """
    import supabase_data
    import requests
    data = request.get_json(silent=True) or {}
    access_token = (data.get('access_token') or request.headers.get('Authorization') or '').replace('Bearer ', '').strip()
    first_name = (data.get('first_name') or '').strip()
    last_name = (data.get('last_name') or '').strip()
    email = (data.get('email') or '').strip().lower()
    phone = (data.get('phone') or '0000000000').strip() or '0000000000'
    password = data.get('password') or ''
    if not email or not password:
        return jsonify({'detail': 'Email and password required'}), 400
    if not SUPABASE_URL or not SUPABASE_ANON_KEY:
        return jsonify({'detail': 'Supabase not configured'}), 500
    if not access_token:
        return jsonify({
            'detail': 'No session from sign up. In Supabase Dashboard go to Authentication → Providers → Email and turn OFF "Confirm email", then try creating an account again.'
        }), 400
    user_id = supabase_data._gen_user_id(phone)
    password_hash = supabase_data._hash_password(password)
    row = {
        'first_name': first_name,
        'last_name': last_name,
        'email': email,
        'phone': phone,
        'password': password_hash,
        'user_id': user_id,
    }
    url = f"{SUPABASE_URL.rstrip('/')}/rest/v1/administrators"
    headers = {
        'apikey': SUPABASE_ANON_KEY,
        'Authorization': f'Bearer {access_token}',
        'Content-Type': 'application/json',
        'Prefer': 'return=minimal',
    }
    try:
        r = requests.post(url, json=row, headers=headers, timeout=10)
        if r.status_code in (200, 201, 204):
            return jsonify({'info': 'Registration', 'Response': 'ok'}), 200
        err = (r.json() or {}).get('message') or r.text or 'Failed'
        return jsonify({'detail': err}), r.status_code if 400 <= r.status_code < 600 else 400
    except Exception as e:
        return jsonify({'detail': str(e)}), 500


@app.route('/auth/clerk/continue', methods=['GET'])
def auth_clerk_continue():
    """Page shown after Clerk sign-in redirect. Gets JWT and POSTs to callback."""
    if not CLERK_PUBLISHABLE_KEY:
        flash('Clerk not configured.')
        return redirect(url_for('login'))
    return render_template('auth_clerk_continue.html', clerk_publishable_key=CLERK_PUBLISHABLE_KEY)


@app.route('/auth/clerk/callback', methods=['POST'])
def auth_clerk_callback():
    """
    Accept Clerk JWT from client, ensure admin exists in administrators,
    store token in session, redirect to dash.
    For new Clerk sign-ups: auto-insert into administrators (RLS admin_insert_self).
    """
    token = None
    email = ''
    first_name = ''
    last_name = ''
    if request.form:
        token = request.form.get('access_token')
        email = (request.form.get('clerk_email') or '').strip().lower()
        first_name = (request.form.get('clerk_first_name') or '').strip()
        last_name = (request.form.get('clerk_last_name') or '').strip()
    if not token and request.is_json:
        try:
            data = request.get_json(silent=True) or {}
            token = data.get('access_token')
            email = (data.get('email') or '').strip().lower()
            first_name = (data.get('first_name') or '').strip()
            last_name = (data.get('last_name') or '').strip()
        except Exception:
            pass
    if not token:
        flash('Missing token. Sign in with Clerk and try again.')
        return redirect(url_for('login'))
    token = str(token).strip()
    if not token:
        flash('Invalid token. Sign in with Clerk and try again.')
        return redirect(url_for('login'))
    try:
        import supabase_data
        supabase_data.ensure_clerk_admin_in_table(token, email=email, first_name=first_name, last_name=last_name)
    except Exception as e:
        if app.config.get('DEBUG'):
            flash(f'Admin setup: {e}')
        else:
            flash('Could not verify admin. Contact support.')
        return redirect(url_for('login'))
    try:
        session.clear()
        session['token'] = {'Authorization': 'Bearer ' + token}
        session.modified = True
        return redirect(url_for('dash'))
    except Exception:
        flash('Sign-in error. Please try again.')
        return redirect(url_for('login'))


@app.route('/logout')
def logout():
    session.pop('token', None)
    return redirect(url_for('login'))


# PWA
_PUBLIC = os.path.join(_ROOT, 'public')


@app.route('/manifest.json')
def pwa_manifest():
    path = os.path.join(_PUBLIC, 'manifest.json')
    if os.path.isfile(path):
        return send_from_directory(_PUBLIC, 'manifest.json', mimetype='application/manifest+json')
    return jsonify({'name': 'ConnectHer Admin', 'short_name': 'ConnectHer Admin', 'start_url': '/', 'display': 'standalone'}), 200


@app.route('/sw.js')
def pwa_sw():
    path = os.path.join(_PUBLIC, 'sw.js')
    if os.path.isfile(path):
        r = send_from_directory(_PUBLIC, 'sw.js', mimetype='application/javascript')
        r.headers['Cache-Control'] = 'no-store, max-age=0'
        return r
    return '', 404


# Redirects for old URLs
@app.route('/registration')
@app.route('/registration/clerk')
@app.route('/preview')
@app.route('/preview/login')
@app.route('/sign_in', methods=['POST', 'GET'])
def legacy_redirects():
    return redirect(url_for('login'))


@app.route('/preview/jobs/complete')
@app.route('/preview/provider/edit/<user_id>')
@app.route('/preview/provider/approve/<user_id>')
@app.route('/preview/subscription/plans')
@app.route('/preview/subscription/plans/add')
@app.route('/preview/subscription/plans/edit/<plan_id>')
@app.route('/preview/subscription/user-subscriptions')
@app.route('/preview/subscription/user-subscriptions/grant')
@app.route('/preview/subscription/platform-settings')
@app.route('/preview/subscription/tracking')
def preview_redirects():
    return redirect(url_for('login'))


@app.route('/sign_up', methods=['GET', 'POST'])
def sign_up():
    flash('Admin registration is not available. Please contact your administrator.')
    return redirect(url_for('login'))


@app.route('/dash')
def dash():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin()
    if not user:
        user = {}
    quotes_count = 0
    users_count = 0
    approvals = []
    try:
        providers_list = supabase_data.list_providers(session)
        app_users_list = supabase_data.list_app_users(session)
        users_count = len(providers_list) + len(app_users_list)
        approvals = supabase_data.list_approvals(session)
        pending_list = supabase_data.all_pending_jobs(session)
        complete_list = supabase_data.all_complete_jobs(session)
        quotes_count = len(pending_list) + len(complete_list)
    except Exception:
        providers_list = []
        app_users_list = []
        pending_list = []
        complete_list = []

    providers_count = len(providers_list) if providers_list else 0
    app_users_count = len(app_users_list) if app_users_list else 0
    pending_jobs_count = len(pending_list) if pending_list else 0
    complete_jobs_count = len(complete_list) if complete_list else 0
    chart_data = {
        'providers': providers_count,
        'app_users': app_users_count,
        'pending_jobs': pending_jobs_count,
        'complete_jobs': complete_jobs_count,
        'approvals_count': len(approvals) if approvals else 0,
    }

    return render_template('landing.html',
        active_d='active', name=user.get('name', user.get('email', 'Admin')), user=user,
        balance=0, context={}, chart_data=chart_data,
        quotes_count=quotes_count, users_count=users_count, approvals=approvals,
        active_p='', active_b='', active_ap='', active_pol='', active_sub='',
        backend_ok=True)


@app.route('/profile')
def profile():
    redir = _require_auth()
    if redir:
        return redir
    user = _current_admin() or {}
    return render_template('profile.html', user=user)


@app.route('/app_users')
def app_users():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        app_users = supabase_data.list_app_users(session)
    except Exception:
        app_users = []
    return render_template('appusers.html', user=user, app_users=app_users)


@app.route('/app_users/<int:internal_id>', methods=['GET', 'POST'])
def app_user_detail(internal_id):
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    admin = _current_admin() or {}
    try:
        appu = supabase_data.get_app_user_detail(session, internal_id)
    except Exception:
        appu = None
    if not appu:
        flash('User not found.')
        return redirect(url_for('app_users'))
    if request.method == 'POST' and request.form.get('_action') == 'update_free_connects':
        patch = {}
        if request.form.get('clear_free_granted') == '1':
            patch['free_connects_granted'] = None
        elif request.form.get('free_connects_granted', '').strip() != '':
            try:
                patch['free_connects_granted'] = int(request.form.get('free_connects_granted'))
            except (ValueError, TypeError):
                flash('Invalid free connects granted value.')
                return redirect(url_for('app_user_detail', internal_id=internal_id))
        if request.form.get('free_connects_used', '').strip() != '':
            try:
                patch['free_connects_used'] = int(request.form.get('free_connects_used'))
            except (ValueError, TypeError):
                flash('Invalid free connects used value.')
                return redirect(url_for('app_user_detail', internal_id=internal_id))
        try:
            if not patch:
                flash('No changes selected.')
            elif supabase_data.patch_user_free_connects(session, internal_id, patch):
                flash('Free connects updated.')
            else:
                flash('Update failed.')
        except Exception as e:
            flash(f'Error: {e}')
        return redirect(url_for('app_user_detail', internal_id=internal_id))
    return render_template(
        'app_user_detail.html',
        user=admin,
        name=admin.get('name', admin.get('email', 'Admin')),
        appu=appu,
        active_d='',
        active_p='',
        active_b='',
        active_ap='',
        active_sub='active',
    )


@app.route('/update/user-data/<user_id>')
def edit_user(user_id):
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        provider = supabase_data.get_provider(session, user_id) or {}
    except Exception:
        provider = {}
    try:
        services = supabase_data.list_services(session)
    except Exception:
        services = []
    return render_template('providerzoom.html', user=user, provider=provider, services=services)


@app.route('/flush/provider/', methods=['GET', 'POST'])
def flush_provider():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    subs = request.form.getlist('subscriptions')
    payload = {
        'first_name': request.form.get('first_name'),
        'last_name': request.form.get('last_name'),
        'phone': request.form.get('phone'),
        'county': request.form.get('county'),
        'area_name': request.form.get('area_name'),
        'nat_id': request.form.get('nat_id'),
        'emm_cont_1': request.form.get('emm_cont_1'),
        'emm_cont_2': request.form.get('emm_cont_2'),
        'user_id': request.form.get('user_id'),
        'title': request.form.get('title'),
        'subs': subs,
    }
    try:
        supabase_data.update_provider(session, payload)
    except Exception:
        pass
    return redirect(url_for('service_providers'))


@app.route('/services')
def services():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        services_list = supabase_data.list_services(session)
    except Exception:
        services_list = []
    return render_template('services.html', user=user, services=services_list)


@app.route('/service/details/<service_id>')
def service(service_id):
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        service_obj = supabase_data.get_service(session, int(service_id)) or {}
    except Exception:
        service_obj = {}
    return render_template('service_Zoom.html', user=user, service=service_obj)


@app.route('/legacy/service-images/<path:filename>')
def legacy_service_local_image(filename):
    """Serve images saved before Supabase Storage (filename only in DB)."""
    redir = _require_auth()
    if redir:
        return redir
    from werkzeug.utils import secure_filename
    safe = secure_filename(os.path.basename(filename))
    if not safe:
        abort(404)
    pic_dir = os.path.join(_ROOT, 'service_images')
    path = os.path.join(pic_dir, safe)
    if not os.path.isfile(path):
        abort(404)
    return send_from_directory(pic_dir, safe, max_age=86400)


@app.route('/add/service/', methods=['GET', 'POST'])
def add_service():
    redir = _require_auth()
    if redir:
        return redir
    from werkzeug.utils import secure_filename
    import supabase_data
    if request.method == 'POST':
        name = (request.form.get('name') or '').strip()
        min_price = request.form.get('min_price')
        description = (request.form.get('description') or '').strip()
        if not name:
            flash('Please enter a service name.', 'error')
            return redirect(url_for('add_service_page'))
        try:
            price_val = float(min_price or 0)
        except (TypeError, ValueError):
            flash('Minimum price must be a number.', 'error')
            return redirect(url_for('add_service_page'))
        payload = {'description': description or None, 'min_price': price_val, 'name': name}
        service_pic = request.files.get('service_pic')
        if service_pic and service_pic.filename:
            raw = service_pic.read()
            if not raw:
                flash('Selected image file was empty.', 'error')
                return redirect(url_for('add_service_page'))
            fn = secure_filename(str(uuid.uuid4()) + '_' + service_pic.filename)
            ct = service_pic.content_type or mimetypes.guess_type(fn)[0] or 'application/octet-stream'
            pic_url, err = supabase_data.upload_service_catalog_public_url(raw, fn, ct)
            if pic_url:
                payload['service_pic'] = pic_url
            else:
                pic_dir = os.path.join(_ROOT, 'service_images')
                os.makedirs(pic_dir, exist_ok=True)
                payload['service_pic'] = fn
                with open(os.path.join(pic_dir, fn), 'wb') as out:
                    out.write(raw)
                flash(
                    'Image saved on the portal only. For the mobile app to show it, set '
                    'SUPABASE_SERVICE_ROLE_KEY in .env and ensure the service_catalog bucket exists '
                    f'({err or "upload failed"}).',
                    'warning',
                )
        try:
            if supabase_data.add_service(session, payload):
                flash('Service created.', 'success')
            else:
                flash('Could not create service. Check that you are signed in as an admin and Supabase is configured.', 'error')
        except Exception as ex:
            logger.exception('add_service')
            flash(f'Could not create service: {ex}', 'error')
        return redirect(url_for('services'))
    return redirect(url_for('add_service_page'))


@app.route('/form/service/', methods=['GET', 'POST'])
def add_service_page():
    redir = _require_auth()
    if redir:
        return redir
    user = _current_admin() or {}
    return render_template('add_Service.html', user=user)


@app.route('/service/update/<service_id>', methods=['GET', 'POST'])
def update_service(service_id):
    redir = _require_auth()
    if redir:
        return redir
    from werkzeug.utils import secure_filename
    import supabase_data
    try:
        price_val = float(request.form.get('min_price') or 0)
    except (TypeError, ValueError):
        flash('Minimum price must be a number.', 'error')
        return redirect(url_for('service', service_id=service_id))
    payload = {
        'description': (request.form.get('description') or '').strip() or None,
        'min_price': price_val,
    }
    service_pic = request.files.get('service_pic')
    if service_pic and service_pic.filename:
        raw = service_pic.read()
        if raw:
            fn = secure_filename(str(uuid.uuid4()) + '_' + service_pic.filename)
            ct = service_pic.content_type or mimetypes.guess_type(fn)[0] or 'application/octet-stream'
            pic_url, err = supabase_data.upload_service_catalog_public_url(raw, fn, ct)
            if pic_url:
                payload['service_pic'] = pic_url
                flash('Photo updated — new image is live for the app.', 'success')
            else:
                pic_dir = os.path.join(_ROOT, 'service_images')
                os.makedirs(pic_dir, exist_ok=True)
                payload['service_pic'] = fn
                with open(os.path.join(pic_dir, fn), 'wb') as out:
                    out.write(raw)
                flash(
                    'Image saved on the portal only. Set SUPABASE_SERVICE_ROLE_KEY for app-visible URLs. '
                    + (err or ''),
                    'warning',
                )
    try:
        if supabase_data.update_service(session, int(service_id), payload):
            if 'service_pic' not in payload:
                flash('Service updated.', 'success')
        else:
            flash('Update failed. Ensure admin access / Supabase is configured.', 'error')
    except Exception as ex:
        logger.exception('update_service')
        flash(f'Update failed: {ex}', 'error')
    return redirect(url_for('service', service_id=service_id))


@app.route('/service/delete/<service_id>', methods=['POST'])
def delete_service(service_id):
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    try:
        ok, err = supabase_data.delete_service(session, int(service_id))
        if ok:
            flash('Service deleted successfully.', 'success')
        else:
            flash(err or 'Could not delete service.', 'error')
    except Exception:
        flash('Could not delete service.', 'error')
    return redirect(url_for('services'))


@app.route('/emergency-hotlines')
def emergency_hotlines():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    nm = user.get('name', user.get('email', 'Admin'))
    try:
        hotlines = supabase_data.list_emergency_hotlines(session)
    except Exception:
        hotlines = []
    return render_template(
        'emergency_hotlines.html',
        user=user,
        name=nm,
        hotlines=hotlines,
        active_d='',
        active_p='',
        active_b='',
        active_ap='',
        active_sub='',
        active_hotlines='active',
    )


@app.route('/emergency-hotlines/partial')
def emergency_hotlines_partial():
    """HTMX fragment: list body only, for auto-refresh without full page reload."""
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    try:
        hotlines = supabase_data.list_emergency_hotlines(session)
    except Exception:
        hotlines = []
    return render_template('_emergency_hotlines_partial.html', hotlines=hotlines)


@app.route('/emergency-hotlines/add', methods=['GET', 'POST'])
def emergency_hotline_add():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    nm = user.get('name', user.get('email', 'Admin'))
    if request.method == 'POST':
        name = (request.form.get('name') or '').strip()
        phone = (request.form.get('phone') or '').strip()
        if not name or not phone:
            flash('Name and phone are required.', 'error')
        else:
            try:
                sort_val = int(request.form.get('sort_order') or 0)
            except (ValueError, TypeError):
                sort_val = 0
            payload = {
                'name': name,
                'description': (request.form.get('description') or '').strip() or None,
                'phone': phone,
                'sort_order': sort_val,
                'is_active': request.form.get('is_active') == '1',
            }
            try:
                if supabase_data.add_emergency_hotline(session, payload):
                    flash('Hotline created.', 'success')
                    return redirect(url_for('emergency_hotlines'))
                flash('Could not create hotline. Run sql/supabase_emergency_hotlines.sql in Supabase if the table is missing.', 'error')
            except Exception:
                flash('Could not create hotline.', 'error')
    return render_template(
        'emergency_hotline_form.html',
        user=user,
        name=nm,
        line=None,
        is_edit=False,
        active_d='',
        active_p='',
        active_b='',
        active_ap='',
        active_sub='',
        active_hotlines='active',
    )


@app.route('/emergency-hotlines/edit/<hotline_id>', methods=['GET', 'POST'])
def emergency_hotline_edit(hotline_id):
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    nm = user.get('name', user.get('email', 'Admin'))
    try:
        line = supabase_data.get_emergency_hotline(session, hotline_id)
    except Exception:
        line = None
    if not line:
        flash('Hotline not found.', 'error')
        return redirect(url_for('emergency_hotlines'))
    if request.method == 'POST':
        if request.form.get('_action') == 'delete':
            try:
                if supabase_data.delete_emergency_hotline(session, hotline_id):
                    flash('Hotline deleted.', 'success')
                else:
                    flash('Could not delete hotline.', 'error')
            except Exception:
                flash('Could not delete hotline.', 'error')
            return redirect(url_for('emergency_hotlines'))
        name = (request.form.get('name') or '').strip()
        phone = (request.form.get('phone') or '').strip()
        if not name or not phone:
            flash('Name and phone are required.', 'error')
        else:
            try:
                sort_val = int(request.form.get('sort_order') or 0)
            except (ValueError, TypeError):
                sort_val = 0
            payload = {
                'name': name,
                'description': (request.form.get('description') or '').strip() or None,
                'phone': phone,
                'sort_order': sort_val,
                'is_active': request.form.get('is_active') == '1',
            }
            try:
                if supabase_data.update_emergency_hotline(session, hotline_id, payload):
                    flash('Hotline updated.', 'success')
                    return redirect(url_for('emergency_hotlines'))
                flash('Could not update hotline.', 'error')
            except Exception:
                flash('Could not update hotline.', 'error')
        line = supabase_data.get_emergency_hotline(session, hotline_id) or line
    return render_template(
        'emergency_hotline_form.html',
        user=user,
        name=nm,
        line=line,
        is_edit=True,
        active_d='',
        active_p='',
        active_b='',
        active_ap='',
        active_sub='',
        active_hotlines='active',
    )


@app.route('/provider', methods=['GET'])
def provider_page():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        services_list = supabase_data.list_services(session)
    except Exception:
        services_list = []
    try:
        documents = supabase_data.list_document_types(session)
    except Exception:
        documents = []
    return render_template('registration.html', user=user, services=services_list, documents=documents)


@app.route('/add/provider', methods=['GET', 'POST'])
def add_provider():
    redir = _require_auth()
    if redir:
        return redir
    from werkzeug.utils import secure_filename
    import supabase_data
    if request.method == 'POST':
        kyc = {
            'first_name': request.form.get('first_name'),
            'last_name': request.form.get('last_name'),
            'phone': request.form.get('phone'),
            'email': request.form.get('email'),
            'title': request.form.get('title'),
            'gender': request.form.get('gender'),
            'birth_date': request.form.get('birth_date') or request.form.get('dob'),
            'country': request.form.get('country'),
            'county': request.form.get('county'),
            'area_name': request.form.get('mtaa'),
            'nat_id': request.form.get('nat_id'),
            'emm_cont_1': request.form.get('emm_cont_1'),
            'emm_cont_2': request.form.get('emm_cont_2'),
        }
        user_loc = {}
        lat, lon = request.form.get('lat'), request.form.get('long')
        if lat and lon:
            try:
                user_loc = {'latitude': float(lat), 'longitude': float(lon)}
            except ValueError:
                pass
        subs = request.form.getlist('subscriptions')
        prof_loc = os.path.join(_ROOT, 'profs')
        doc_loc = os.path.join(_ROOT, 'Docs')
        os.makedirs(prof_loc, exist_ok=True)
        os.makedirs(doc_loc, exist_ok=True)
        clean_docs = []
        for key, f in (request.files or {}).items():
            if f and f.filename:
                if key == 'profpic':
                    ext = (f.filename.rsplit('.', 1) + ['png'])[1].lower()
                    fn = secure_filename(f'{uuid.uuid4()}.{ext}')
                    f.save(os.path.join(prof_loc, fn))
                    kyc['prof_pic'] = fn
                else:
                    ext = (f.filename.rsplit('.', 1) + ['pdf'])[1].lower()
                    fn = secure_filename(f'{uuid.uuid4()}.{ext}')
                    f.save(os.path.join(doc_loc, fn))
                    clean_docs.append({'doc_type_id': key, 'name': fn})
        msg, uid = supabase_data.add_provider(session, kyc, clean_docs, user_loc, subs)
        flash(msg if msg else 'User created')
        return redirect(url_for('provider_page'))
    return redirect(url_for('provider_page'))


@app.route('/providers', methods=['GET', 'POST'])
def service_providers():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        providers = supabase_data.list_providers(session)
    except Exception:
        providers = []
    return render_template('providers.html', user=user, providers=providers)


def _normalize_approval(a):
    """Ensure approval is a dict with safe keys so the template never errors."""
    if not isinstance(a, dict):
        return None
    return {
        'first_name': a.get('first_name') if a.get('first_name') is not None else '',
        'last_name': a.get('last_name') if a.get('last_name') is not None else '',
        'phone': a.get('phone') if a.get('phone') is not None else '',
        'email': a.get('email') if a.get('email') is not None else '',
        'user_id': a.get('user_id'),
        'id': a.get('id'),
    }


@app.route('/provider/approvals/', methods=['GET', 'POST'])
def get_approvals():
    redir = _require_auth()
    if redir:
        return redir
    user = {}
    approvals = []
    try:
        import supabase_data
        user = _current_admin() or {}
        raw = supabase_data.list_approvals(session)
        if isinstance(raw, list):
            approvals = [a for a in (_normalize_approval(x) for x in raw) if a is not None]
    except Exception as e:
        logger.exception("Approvals page error: %s", e)
    return render_template('approvals.html', user=user, approvals=approvals)


@app.route('/provider/approve/by-user-id/<user_id_str>', methods=['GET'])
def approve_by_user_id(user_id_str):
    """Resolve internal id from user_id (string) and redirect to approve page. Use when id is missing in list."""
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    internal_id = supabase_data.get_user_internal_id(session, user_id_str)
    if internal_id is not None:
        return redirect(url_for('approve_page', internal_id=internal_id))
    flash('User not found.', 'error')
    return redirect(url_for('get_approvals'))


@app.route('/provider/approve/<int:internal_id>', methods=['GET', 'POST'])
def approve_page(internal_id):
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    application_documents = []
    try:
        provider = supabase_data.get_provider_by_id(session, internal_id) or {}
        services = supabase_data.list_services(session)
        application = supabase_data.get_pending_application_by_internal_id(session, internal_id)
        application_service_ids = (application.get('service_ids') or []) if application else []
        application_documents = supabase_data.list_user_verification_documents(session, internal_id)
    except Exception:
        provider = {}
        services = []
        application_service_ids = []
    return render_template(
        'Approve.html',
        user=user,
        provider=provider,
        services=services,
        application_service_ids=application_service_ids,
        application_documents=application_documents,
        internal_id=internal_id,
    )


def _get_fcm_access_token():
    """Get OAuth2 access token for FCM v1 API using Firebase service account."""
    try:
        from google.oauth2 import service_account as sa
        import google.auth.transport.requests as g_requests

        sa_path = os.path.join(_ROOT, 'firebase_service_account.json')
        if not os.path.isfile(sa_path):
            logger.warning("firebase_service_account.json not found at %s", sa_path)
            return None, None

        creds = sa.Credentials.from_service_account_file(
            sa_path,
            scopes=['https://www.googleapis.com/auth/firebase.messaging'],
        )
        creds.refresh(g_requests.Request())

        import json
        with open(sa_path) as f:
            project_id = json.load(f).get('project_id')

        return creds.token, project_id
    except Exception as e:
        logger.warning("FCM auth failed: %s", e)
        return None, None


def _send_fcm_message(access_token, project_id, device_token, title, body, data=None):
    """Send a single FCM v1 message. Returns True on success."""
    import requests as req
    url = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
    message = {
        'message': {
            'token': device_token,
            'notification': {'title': title, 'body': body},
            'data': {k: str(v) for k, v in (data or {}).items()},
            'android': {'priority': 'high'},
        }
    }
    try:
        r = req.post(url, json=message, headers={
            'Authorization': f'Bearer {access_token}',
            'Content-Type': 'application/json',
        }, timeout=10)
        if r.status_code == 200:
            return True
        logger.warning("FCM send failed: status=%s body=%s", r.status_code, r.text[:300])
        return False
    except Exception as e:
        logger.warning("FCM send exception: %s", e)
        return False


def _notify_via_edge_function(internal_user_id, title, body, data=None):
    """Fallback: call the notify-app-user Edge Function."""
    import requests as req
    supa_url = (os.environ.get('SUPABASE_URL') or '').strip().rstrip('/')
    anon_key = (os.environ.get('SUPABASE_ANON_KEY') or '').strip()
    if not supa_url or not anon_key:
        return 'skipped'
    try:
        r = req.post(
            f"{supa_url}/functions/v1/notify-app-user",
            json={'user_id': internal_user_id, 'title': title, 'body': body, 'data': data or {}},
            headers={'Content-Type': 'application/json', 'Authorization': f'Bearer {anon_key}'},
            timeout=20,
        )
        if r.status_code == 200:
            resp = r.json()
            sent = resp.get('sent', 0)
            if sent and sent > 0:
                logger.info("Edge Function notify OK: user_id=%s sent=%s", internal_user_id, sent)
                return 'sent'
            logger.info("Edge Function: no devices for user_id=%s", internal_user_id)
            return 'no_devices'
        logger.warning("Edge Function failed: status=%s body=%s", r.status_code, r.text[:300])
        return 'error'
    except Exception as e:
        logger.warning("Edge Function exception: %s", e)
        return 'error'


def _notify_provider_approved(internal_user_id):
    """
    Send FCM push notification to the provider. Tries direct FCM first, falls back to Edge Function.
    Returns: 'sent', 'no_devices', 'error', or 'skipped'.
    """
    import requests as req

    title = 'Application approved'
    body = 'Your provider application has been approved. You can now offer services.'
    fcm_data = {'type': 'provider_approved'}

    supa_url = (os.environ.get('SUPABASE_URL') or '').strip().rstrip('/')
    anon_key = (os.environ.get('SUPABASE_ANON_KEY') or '').strip()
    if not supa_url or not anon_key:
        logger.warning("Notify skipped: SUPABASE_URL or SUPABASE_ANON_KEY not set")
        return 'skipped'

    access_token, project_id = _get_fcm_access_token()
    if not access_token or not project_id:
        logger.warning("Direct FCM auth failed, falling back to Edge Function")
        return _notify_via_edge_function(internal_user_id, title, body, fcm_data)

    try:
        tok = session.get('token', {})
        auth_header = tok.get('Authorization', f'Bearer {anon_key}')
        r = req.get(
            f"{supa_url}/rest/v1/devices",
            params={'user_id': f'eq.{internal_user_id}', 'select': 'reg_token'},
            headers={
                'apikey': anon_key,
                'Authorization': auth_header,
                'Content-Type': 'application/json',
            },
            timeout=10,
        )
        if r.status_code != 200:
            logger.warning("Failed to fetch devices (status=%s), falling back to Edge Function", r.status_code)
            return _notify_via_edge_function(internal_user_id, title, body, fcm_data)

        devices = r.json()
        tokens = [d['reg_token'] for d in devices if d.get('reg_token')]
    except Exception as e:
        logger.warning("Device lookup failed (%s), falling back to Edge Function", e)
        return _notify_via_edge_function(internal_user_id, title, body, fcm_data)

    if not tokens:
        logger.info("No devices registered for user_id=%s", internal_user_id)
        return 'no_devices'

    sent_count = 0
    for token in tokens:
        if _send_fcm_message(access_token, project_id, token, title, body, data=fcm_data):
            sent_count += 1

    if sent_count > 0:
        logger.info("Direct FCM sent to %d/%d devices for user_id=%s", sent_count, len(tokens), internal_user_id)
        return 'sent'

    logger.warning("Direct FCM failed for all devices, falling back to Edge Function")
    return _notify_via_edge_function(internal_user_id, title, body, fcm_data)


@app.route('/provider/approve', methods=['POST'])
def approve():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    try:
        internal_id = request.form.get('internal_id')
        if internal_id is not None:
            internal_id = int(internal_id)
        else:
            internal_id = None
    except (TypeError, ValueError):
        internal_id = None
    if internal_id is None:
        flash('Invalid request. Please use the Approvals list to review an application.')
        return redirect(url_for('get_approvals'))
    subs = request.form.getlist('subscriptions')
    # If admin selected no checkboxes, use the applicant's stored service_ids so provider still gets subscriptions
    if not subs:
        application = supabase_data.get_pending_application_by_internal_id(session, internal_id)
        if application and application.get('service_ids'):
            subs = [str(sid) for sid in application['service_ids']]
    service_ids = [s for s in subs if s and str(s).strip()]
    try:
        ok = supabase_data.approve_provider_by_id(session, internal_id, service_ids=service_ids if service_ids else None)
        if ok:
            notify_result = _notify_provider_approved(internal_id)
            if notify_result == 'no_devices':
                flash('Provider approved. Push notification was not sent (no device registered). Ask them to open the app.')
            elif notify_result in ('error', 'skipped'):
                flash('Provider approved. Push notification may not have been sent; check server logs if needed.')
        else:
            flash('Approval failed. Please try again.')
    except Exception:
        flash('An error occurred during approval.')
    return redirect(url_for('service_providers'))


@app.route('/jobs/pending/', methods=['GET', 'POST'])
def pending_jobs():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        jobs = supabase_data.all_pending_jobs(session)
    except Exception:
        jobs = []
    return render_template('ongoingjobs.html', user=user, jobs=jobs)


@app.route('/jobs/complete/', methods=['GET', 'POST'])
def complete_jobs():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        jobs = supabase_data.all_complete_jobs(session)
    except Exception:
        jobs = []
    return render_template('completejobs.html', user=user, jobs=jobs)


@app.route('/subscription/platform-settings', methods=['GET', 'POST'])
def subscription_platform_settings():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    if request.method == 'POST':
        # Must run before the save branch: this form does not include free_tier_connects;
        # otherwise get(..., 0) would PATCH platform_settings to 0.
        if request.form.get('_action') == 'apply_to_all':
            if supabase_data.apply_platform_free_tier_to_all_users(session):
                flash('Applied platform free-tier cap to all users with an existing allocation.')
            else:
                flash('Failed to apply to all users.')
            return redirect(url_for('subscription_platform_settings'))
        raw = request.form.get('free_tier_connects')
        if raw is None or (isinstance(raw, str) and not raw.strip()):
            flash('Enter a valid default free connects value.')
            return redirect(url_for('subscription_platform_settings'))
        try:
            v = int(raw)
        except (ValueError, TypeError):
            flash('Invalid number.')
            return redirect(url_for('subscription_platform_settings'))
        if supabase_data.update_platform_settings(session, v):
            flash('Free tier default updated. Existing users keep their current cap unless you apply below.')
        else:
            flash('Failed to update platform settings.')
        return redirect(url_for('subscription_platform_settings'))
    try:
        settings = supabase_data.get_platform_settings(session)
    except Exception:
        settings = {'id': 1, 'free_tier_connects': 5, 'updated_at': None}
    return render_template(
        'subscription_platform_settings.html',
        user=user,
        name=user.get('name', user.get('email', 'Admin')),
        settings=settings,
        active_d='',
        active_p='',
        active_b='',
        active_ap='',
        active_sub='active',
    )


@app.route('/booking-requests')
def booking_requests():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    status_filter = request.args.get('status', '')
    try:
        requests_list = supabase_data.list_booking_requests(session, status_filter=status_filter or None)
    except Exception:
        requests_list = []
    return render_template(
        'booking_requests.html',
        user=user,
        name=user.get('name', user.get('email', 'Admin')),
        requests=requests_list,
        status_filter=status_filter,
        active_d='',
        active_p='',
        active_b='active',
        active_ap='',
        active_sub='',
    )


@app.route('/subscription/plans')
def subscription_plans():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        plans = supabase_data.list_subscription_plans(session)
    except Exception:
        plans = []
    return render_template('subscription_plans.html', user=user, name=user.get('name', user.get('email', 'Admin')),
        plans=plans, active_d='', active_p='', active_b='', active_ap='', active_sub='active')


@app.route('/subscription/plans/add', methods=['GET', 'POST'])
def subscription_plan_add():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    if request.method == 'POST':
        name = request.form.get('name', '').strip()
        if not name:
            flash('Name is required.')
        else:
            try:
                features_text = request.form.get('features', '')
                features = [f.strip() for f in features_text.split('\n') if f.strip()] if features_text else []
                payload = {
                    'name': name,
                    'description': request.form.get('description', '').strip() or None,
                    'price': request.form.get('price', 0),
                    'currency': request.form.get('currency', 'KES'),
                    'duration_type': request.form.get('duration_type', 'month'),
                    'duration_value': request.form.get('duration_value', 1),
                    'features': features,
                    'is_active': request.form.get('is_active') == '1',
                    'is_popular': request.form.get('is_popular') == '1',
                    'sort_order': request.form.get('sort_order', 0),
                    'connects_limit_enabled': request.form.get('connects_limit_enabled') == '1',
                    'connects_per_period': request.form.get('connects_per_period'),
                    'connects_period_rule': request.form.get('connects_period_rule', 'subscription_term'),
                }
                if supabase_data.add_subscription_plan(session, payload):
                    flash('Plan created successfully.')
                    return redirect(url_for('subscription_plans'))
                flash('Failed to create plan.')
            except Exception as e:
                flash(f'Error: {e}')
    return render_template('subscription_plan_form.html', user=user, name=user.get('name', user.get('email', 'Admin')),
        plan=None, is_edit=False, active_d='', active_p='', active_b='', active_ap='', active_sub='active')


@app.route('/subscription/plans/edit/<plan_id>', methods=['GET', 'POST'])
def subscription_plan_edit(plan_id):
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        plan = supabase_data.get_subscription_plan(session, plan_id)
    except Exception:
        plan = None
    if not plan:
        flash('Plan not found.')
        return redirect(url_for('subscription_plans'))
    if request.method == 'POST':
        action = request.form.get('_action')
        if action == 'delete':
            if supabase_data.delete_subscription_plan(session, plan_id):
                flash('Plan deleted.')
            else:
                flash('Failed to delete (may have active subscriptions).')
            return redirect(url_for('subscription_plans'))
        if action == 'toggle_active':
            new_active = not plan.get('is_active', True)
            if supabase_data.update_subscription_plan(session, plan_id, {'is_active': new_active}):
                flash('Plan ' + ('activated' if new_active else 'deactivated') + '.')
            return redirect(url_for('subscription_plan_edit', plan_id=plan_id))
        features_text = request.form.get('features', '')
        features = [f.strip() for f in features_text.split('\n') if f.strip()] if features_text else []
        try:
            price_val = float(request.form.get('price') or 0)
        except (ValueError, TypeError):
            price_val = 0
        try:
            duration_val = int(request.form.get('duration_value') or 1)
        except (ValueError, TypeError):
            duration_val = 1
        try:
            sort_val = int(request.form.get('sort_order') or 0)
        except (ValueError, TypeError):
            sort_val = 0
        payload = {
            'name': request.form.get('name', '').strip(),
            'description': request.form.get('description', '').strip() or None,
            'price': price_val,
            'currency': request.form.get('currency', 'KES'),
            'duration_type': request.form.get('duration_type', 'month'),
            'duration_value': duration_val,
            'features': features,
            'is_active': request.form.get('is_active') == '1',
            'is_popular': request.form.get('is_popular') == '1',
            'sort_order': sort_val,
            'connects_limit_enabled': request.form.get('connects_limit_enabled') == '1',
            'connects_per_period': request.form.get('connects_per_period'),
            'connects_period_rule': request.form.get('connects_period_rule', 'subscription_term'),
        }
        if supabase_data.update_subscription_plan(session, plan_id, payload):
            flash('Plan updated.')
            return redirect(url_for('subscription_plans'))
        flash('Failed to update plan.')
    return render_template('subscription_plan_form.html', user=user, name=user.get('name', user.get('email', 'Admin')),
        plan=plan, is_edit=True, active_d='', active_p='', active_b='', active_ap='', active_sub='active')


@app.route('/subscription/user-subscriptions')
def subscription_user_subscriptions():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    status_filter = request.args.get('status', '')
    plan_filter = request.args.get('plan_id', '')
    try:
        plans = supabase_data.list_subscription_plans(session)
        subscriptions = supabase_data.list_user_plan_subscriptions(session, status_filter=status_filter or None, plan_filter=plan_filter or None)
    except Exception:
        plans = []
        subscriptions = []
    return render_template('subscription_user_subscriptions.html', user=user, name=user.get('name', user.get('email', 'Admin')),
        subscriptions=subscriptions, status_filter=status_filter, plan_filter=plan_filter,
        plans=plans, active_d='', active_p='', active_b='', active_ap='', active_sub='active')


@app.route('/subscription/tracking')
def subscription_tracking():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        dash = supabase_data.subscription_tracking_dashboard(session)
    except Exception:
        dash = {
            'total_sub_rows': 0,
            'by_status': {},
            'by_plan_rows': [],
            'connects_snapshot': [],
            'recent_paystack': [],
            'plans': [],
        }
    return render_template(
        'subscription_tracking.html',
        user=user,
        name=user.get('name', user.get('email', 'Admin')),
        dash=dash,
        active_d='',
        active_p='',
        active_b='',
        active_ap='',
        active_sub='active',
    )


@app.route('/subscription/user-subscriptions/grant', methods=['GET', 'POST'])
def subscription_grant():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        app_users = supabase_data.list_users_for_plan_grant(session)
        plans = supabase_data.list_subscription_plans(session)
    except Exception:
        app_users = []
        plans = []
    if request.method == 'POST':
        user_id = request.form.get('user_id')
        plan_id = request.form.get('plan_id')
        started_at = request.form.get('started_at')
        expires_at = request.form.get('expires_at')
        if not all([user_id, plan_id, started_at, expires_at]):
            flash('User, plan, start date, and end date are required.')
        else:
            try:
                if supabase_data.grant_user_plan_subscription(
                    session, user_id, plan_id, started_at, expires_at,
                    payment_reference=request.form.get('payment_reference') or None,
                    notes=request.form.get('notes') or None
                ):
                    flash('Subscription granted successfully.')
                    return redirect(url_for('subscription_user_subscriptions'))
                flash('Failed to grant subscription.')
            except Exception as e:
                flash(f'Error: {e}')
    return render_template('subscription_grant.html', user=user, name=user.get('name', user.get('email', 'Admin')),
        plans=plans, app_users=app_users, active_d='', active_p='', active_b='', active_ap='', active_sub='active')


@app.route('/subscription/user-subscriptions/<sub_id>', methods=['GET', 'POST'])
def subscription_user_detail(sub_id):
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    try:
        sub = supabase_data.get_user_plan_subscription(session, sub_id)
    except Exception:
        sub = None
    if not sub:
        flash('Subscription not found.')
        return redirect(url_for('subscription_user_subscriptions'))
    if request.method == 'POST':
        action = request.form.get('_action')
        if action == 'cancel':
            if supabase_data.cancel_user_plan_subscription(session, sub_id):
                flash('Subscription cancelled.')
            else:
                flash('Failed to cancel.')
            return redirect(url_for('subscription_user_detail', sub_id=sub_id))
        if action == 'extend':
            new_expires = request.form.get('new_expires_at')
            if new_expires and supabase_data.extend_user_plan_subscription(session, sub_id, new_expires):
                flash('Subscription extended.')
            else:
                flash('Invalid date or failed to extend.')
            return redirect(url_for('subscription_user_detail', sub_id=sub_id))
        if action == 'update':
            from datetime import date
            raw_exp = request.form.get('expires_at') or sub.get('expires_at')
            if hasattr(raw_exp, 'strftime'):
                expires_str = raw_exp.strftime('%Y-%m-%d')
            elif raw_exp:
                expires_str = str(raw_exp)[:10]
            else:
                expires_str = sub.get('expires_at')
                if hasattr(expires_str, 'strftime'):
                    expires_str = expires_str.strftime('%Y-%m-%d')
                elif expires_str:
                    expires_str = str(expires_str)[:10]
            new_status = request.form.get('status') or sub.get('status')
            payload = {
                'status': new_status,
                'expires_at': expires_str,
                'payment_reference': request.form.get('payment_reference') or '',
                'notes': request.form.get('notes') or '',
            }
            if new_status == 'cancelled':
                payload['cancelled_at'] = date.today().isoformat()
            if supabase_data.update_user_plan_subscription(session, sub_id, payload):
                flash('Subscription updated.')
            else:
                flash('Failed to update.')
            return redirect(url_for('subscription_user_detail', sub_id=sub_id))
        if action == 'update_connects':
            patch = {}
            if request.form.get('clear_connects_granted') == '1':
                patch['connects_granted'] = None
            elif request.form.get('connects_granted', '').strip() != '':
                try:
                    patch['connects_granted'] = int(request.form.get('connects_granted'))
                except (ValueError, TypeError):
                    flash('Invalid connects granted.')
                    return redirect(url_for('subscription_user_detail', sub_id=sub_id))
            if request.form.get('connects_used', '').strip() != '':
                try:
                    patch['connects_used'] = int(request.form.get('connects_used'))
                except (ValueError, TypeError):
                    flash('Invalid connects used.')
                    return redirect(url_for('subscription_user_detail', sub_id=sub_id))
            ps = request.form.get('connects_period_started_at', '').strip()
            if ps:
                patch['connects_period_started_at'] = ps
            note_line = request.form.get('connect_adjust_note', '').strip()
            if note_line:
                old_notes = sub.get('notes') or ''
                patch['notes'] = (old_notes + '\n' if old_notes else '') + '[connect adjust] ' + note_line
            if not patch:
                flash('No connect changes selected.')
            elif supabase_data.update_user_plan_subscription(session, sub_id, patch):
                flash('Subscription connects updated.')
            else:
                flash('Failed to update connects.')
            return redirect(url_for('subscription_user_detail', sub_id=sub_id))
    sub = supabase_data.get_user_plan_subscription(session, sub_id)
    return render_template('subscription_user_detail.html', user=user, name=user.get('name', user.get('email', 'Admin')),
        sub=sub, active_d='', active_p='', active_b='', active_ap='', active_sub='active')


@app.route('/search', methods=['GET', 'POST'])
def search():
    redir = _require_auth()
    if redir:
        return redir
    import supabase_data
    user = _current_admin() or {}
    query = request.args.get('query') or request.form.get('query') or ''
    try:
        providers = supabase_data.search_providers(session, query) if query else supabase_data.list_providers(session)
    except Exception:
        providers = []
    return render_template('providersearch.html', user=user, providers=providers)


@app.route('/404')
def not_found_page():
    return render_template('404.html')


@app.route('/oops')
def oops():
    return render_template('oops.html')


@app.route('/privacy_statement', methods=['GET'])
def privacy():
    return render_template('PrivacyPolicy.htm')


@app.route('/terms', methods=['GET'])
def term():
    return render_template('terms.html')


@app.route('/quit', methods=['GET'])
def quit():
    return render_template('quitBase.html')


@app.route('/delete/account', methods=['GET'])
def delete_account():
    if 'token' not in session:
        return redirect(url_for('quit'))
    return render_template('quit.html')


@app.route('/delete/', methods=['GET', 'POST'])
def effect_deletion():
    if 'token' not in session:
        return redirect(url_for('quit'))
    return redirect('https://womanshope.org')


@app.route('/quit/sign_in', methods=['POST', 'GET'])
def quit_sign_in():
    if request.method == 'POST':
        flash('Use the main Sign in page.')
    return redirect(url_for('login'))


if __name__ == '__main__':
    app.run()
