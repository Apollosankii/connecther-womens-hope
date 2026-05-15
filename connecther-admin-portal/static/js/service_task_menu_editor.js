/**
 * Visual editor for services.task_menu (ConnectHer mobile ServiceTaskMenuParser contract).
 * Depends on a same-origin POST uploadUrl (multipart field "file").
 */
(function (global) {
  'use strict';

  function defaultMenu() {
    return {
      banner_image_url: '',
      rows: [
        {
          type: 'quantity',
          key: 'units',
          title: 'Service units',
          unit_label: 'units',
          unit_price: 500,
          min: 0,
          max: 99,
          default_qty: 0,
          image_url: '',
        },
      ],
    };
  }

  function parseInitial(text) {
    try {
      var o = JSON.parse(text || '{}');
      if (o && typeof o === 'object' && Array.isArray(o.rows)) return o;
    } catch (e) {}
    return defaultMenu();
  }

  function syncHidden(state, hiddenEl, rawEl) {
    var s = JSON.stringify(state, null, 2);
    hiddenEl.value = s;
    if (rawEl) rawEl.value = s;
  }

  function esc(s) {
    if (s == null) return '';
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/"/g, '&quot;');
  }

  function upload(opts, file, cb) {
    var fd = new FormData();
    fd.append('file', file);
    fetch(opts.uploadUrl, { method: 'POST', body: fd, credentials: 'same-origin' })
      .then(function (r) {
        return r.json().then(function (j) {
          return { ok: r.ok, j: j };
        });
      })
      .then(function (x) {
        if (!x.ok || !x.j.ok) {
          cb(x.j.error || 'Upload failed', null);
          return;
        }
        cb(null, x.j.url);
      })
      .catch(function (e) {
        cb(e.message || 'Network error', null);
      });
  }

  function renderBanner(root, state, opts, hiddenEl, rawEl) {
    var wrap = document.createElement('div');
    wrap.className = 'mb-3';
    wrap.innerHTML =
      '<label class="ch-field-label">Banner image URL (optional)</label>' +
      '<div class="d-flex flex-wrap gap-2 align-items-start">' +
      '<input type="url" class="form-control ch-tm-banner-url" style="flex:1;min-width:200px;background:var(--ch-bg-card);border:1px solid var(--ch-border);color:var(--ch-text);" placeholder="https://...">' +
      '<input type="file" accept="image/*" class="ch-tm-banner-file d-none">' +
      '<button type="button" class="btn btn-outline-secondary ch-tm-banner-upload">Upload</button>' +
      '</div>' +
      '<small class="text-muted d-block mt-1">Shown above the task list in the app. Upload stores the file in Supabase <code>service_catalog</code> and fills the URL.</small>';
    var urlIn = wrap.querySelector('.ch-tm-banner-url');
    var fileIn = wrap.querySelector('.ch-tm-banner-file');
    var btn = wrap.querySelector('.ch-tm-banner-upload');
    urlIn.value = state.banner_image_url || '';
    urlIn.addEventListener('input', function () {
      state.banner_image_url = urlIn.value.trim();
      syncHidden(state, hiddenEl, rawEl);
    });
    btn.addEventListener('click', function () {
      fileIn.click();
    });
    fileIn.addEventListener('change', function () {
      var f = fileIn.files && fileIn.files[0];
      if (!f) return;
      btn.disabled = true;
      upload(opts, f, function (err, url) {
        btn.disabled = false;
        fileIn.value = '';
        if (err) {
          alert(err);
          return;
        }
        state.banner_image_url = url;
        urlIn.value = url;
        syncHidden(state, hiddenEl, rawEl);
      });
    });
    root.appendChild(wrap);
  }

  function rowFieldsHtml(row, idx) {
    var t = row.type || 'quantity';
    if (t === 'section') {
      return (
        '<div class="row g-2">' +
        '<div class="col-md-6"><label class="small text-muted">Title</label><input class="form-control ch-tm-in" data-k="title" value="' +
        esc(row.title) +
        '"></div>' +
        '<div class="col-md-6"><label class="small text-muted">Subtitle (optional)</label><input class="form-control ch-tm-in" data-k="subtitle" value="' +
        esc(row.subtitle || '') +
        '"></div>' +
        '</div>'
      );
    }
    if (t === 'quantity') {
      return (
        '<div class="row g-2">' +
        '<div class="col-md-4"><label class="small text-muted">Key</label><input class="form-control ch-tm-in" data-k="key" value="' +
        esc(row.key) +
        '"></div>' +
        '<div class="col-md-8"><label class="small text-muted">Title</label><input class="form-control ch-tm-in" data-k="title" value="' +
        esc(row.title) +
        '"></div>' +
        '<div class="col-md-6"><label class="small text-muted">Unit label</label><input class="form-control ch-tm-in" data-k="unit_label" value="' +
        esc(row.unit_label || '') +
        '"></div>' +
        '<div class="col-md-6"><label class="small text-muted">Unit price (KES)</label><input type="number" step="0.01" class="form-control ch-tm-in" data-k="unit_price" value="' +
        esc(row.unit_price) +
        '"></div>' +
        '<div class="col-4"><label class="small text-muted">Min</label><input type="number" class="form-control ch-tm-in" data-k="min" value="' +
        esc(row.min) +
        '"></div>' +
        '<div class="col-4"><label class="small text-muted">Max</label><input type="number" class="form-control ch-tm-in" data-k="max" value="' +
        esc(row.max) +
        '"></div>' +
        '<div class="col-4"><label class="small text-muted">Default qty</label><input type="number" class="form-control ch-tm-in" data-k="default_qty" value="' +
        esc(row.default_qty) +
        '"></div>' +
        '<div class="col-12"><label class="small text-muted">Image URL</label><div class="d-flex flex-wrap gap-2">' +
        '<input class="form-control ch-tm-in ch-tm-imgurl" data-k="image_url" style="flex:1;min-width:180px" value="' +
        esc(row.image_url || '') +
        '">' +
        '<input type="file" accept="image/*" class="ch-tm-row-file d-none">' +
        '<button type="button" class="btn btn-sm btn-outline-secondary ch-tm-row-upload">Upload thumb</button></div></div>' +
        '</div>'
      );
    }
    return (
      '<div class="row g-2">' +
      '<div class="col-md-4"><label class="small text-muted">Key</label><input class="form-control ch-tm-in" data-k="key" value="' +
      esc(row.key) +
      '"></div>' +
      '<div class="col-md-8"><label class="small text-muted">Title</label><input class="form-control ch-tm-in" data-k="title" value="' +
      esc(row.title) +
      '"></div>' +
      '<div class="col-md-6"><label class="small text-muted">Unit label</label><input class="form-control ch-tm-in" data-k="unit_label" value="' +
      esc(row.unit_label || '') +
      '"></div>' +
      '<div class="col-md-6"><label class="small text-muted">Unit price (KES)</label><input type="number" step="0.01" class="form-control ch-tm-in" data-k="unit_price" value="' +
      esc(row.unit_price) +
      '"></div>' +
      '<div class="col-12"><label class="small text-muted d-flex align-items-center gap-2"><input type="checkbox" class="ch-tm-toggle-default" ' +
      (row.default_checked ? 'checked' : '') +
      '> Default ON</label></div>' +
      '<div class="col-12"><label class="small text-muted">Image URL</label><div class="d-flex flex-wrap gap-2">' +
      '<input class="form-control ch-tm-in ch-tm-imgurl" data-k="image_url" style="flex:1;min-width:180px" value="' +
      esc(row.image_url || '') +
      '">' +
      '<input type="file" accept="image/*" class="ch-tm-row-file d-none">' +
      '<button type="button" class="btn btn-sm btn-outline-secondary ch-tm-row-upload">Upload thumb</button></div></div>' +
      '</div>'
    );
  }

  function readRowFromCard(card, type) {
    var row = { type: type };
    card.querySelectorAll('.ch-tm-in').forEach(function (inp) {
      var k = inp.getAttribute('data-k');
      if (!k) return;
      if (k === 'unit_price') {
        var n = parseFloat(inp.value);
        row[k] = isNaN(n) ? 0 : n;
      } else if (k === 'min' || k === 'max' || k === 'default_qty') {
        var ni = parseInt(inp.value, 10);
        row[k] = isNaN(ni) ? 0 : ni;
      } else {
        row[k] = inp.value;
      }
    });
    if (type === 'toggle') {
      var c = card.querySelector('.ch-tm-toggle-default');
      row.default_checked = !!(c && c.checked);
    }
    if (type === 'quantity') {
      row.min = Math.max(0, parseInt(row.min, 10) || 0);
      row.max = Math.max(row.min, parseInt(row.max, 10) || 99);
      row.default_qty = Math.max(row.min, Math.min(row.max, parseInt(row.default_qty, 10) || 0));
    }
    if (!row.image_url) delete row.image_url;
    return row;
  }

  function renderRows(root, state, opts, hiddenEl, rawEl, rerender) {
    var list = document.createElement('div');
    list.className = 'ch-tm-rows';
    state.rows.forEach(function (row, idx) {
      var card = document.createElement('div');
      card.className = 'border rounded p-3 mb-2';
      card.style.background = 'var(--ch-bg-muted)';
      card.dataset.idx = String(idx);
      var head = document.createElement('div');
      head.className = 'd-flex flex-wrap justify-content-between align-items-center gap-2 mb-2';
      head.innerHTML =
        '<div class="d-flex align-items-center gap-2">' +
        '<label class="small mb-0">Type</label>' +
        '<select class="form-control form-control-sm ch-tm-type" style="width:auto">' +
        '<option value="section">Section</option><option value="quantity">Quantity</option><option value="toggle">Toggle</option></select>' +
        '</div>' +
        '<div class="btn-group btn-group-sm">' +
        '<button type="button" class="btn btn-outline-secondary ch-tm-up" title="Move up">↑</button>' +
        '<button type="button" class="btn btn-outline-secondary ch-tm-down" title="Move down">↓</button>' +
        '<button type="button" class="btn btn-outline-danger ch-tm-del">Remove</button>' +
        '</div>';
      var typeSel = head.querySelector('.ch-tm-type');
      typeSel.value = row.type || 'quantity';
      var body = document.createElement('div');
      body.className = 'ch-tm-fields';
      body.innerHTML = rowFieldsHtml(row, idx);
      card.appendChild(head);
      card.appendChild(body);

      function wireFields() {
        body.querySelectorAll('.ch-tm-in').forEach(function (inp) {
          inp.addEventListener('input', function () {
            state.rows[idx] = readRowFromCard(card, typeSel.value);
            syncHidden(state, hiddenEl, rawEl);
          });
        });
        var td = body.querySelector('.ch-tm-toggle-default');
        if (td) td.addEventListener('change', refresh);
        var ru = body.querySelector('.ch-tm-row-upload');
        var rf = body.querySelector('.ch-tm-row-file');
        var imgUrl = body.querySelector('.ch-tm-imgurl');
        if (ru && rf) {
          ru.addEventListener('click', function () {
            rf.click();
          });
          rf.addEventListener('change', function () {
            var f = rf.files && rf.files[0];
            if (!f) return;
            ru.disabled = true;
            upload(opts, f, function (err, url) {
              ru.disabled = false;
              rf.value = '';
              if (err) {
                alert(err);
                return;
              }
              if (imgUrl) imgUrl.value = url;
              state.rows[idx] = readRowFromCard(card, typeSel.value);
              syncHidden(state, hiddenEl, rawEl);
            });
          });
        }
      }

      function refresh() {
        state.rows[idx] = readRowFromCard(card, typeSel.value);
        body.innerHTML = rowFieldsHtml(state.rows[idx], idx);
        wireFields();
        syncHidden(state, hiddenEl, rawEl);
      }

      typeSel.addEventListener('change', function () {
        var t = typeSel.value;
        if (t === 'section') {
          state.rows[idx] = { type: 'section', title: state.rows[idx].title || 'Section', subtitle: '' };
        } else if (t === 'toggle') {
          state.rows[idx] = {
            type: 'toggle',
            key: state.rows[idx].key || 't_' + idx,
            title: state.rows[idx].title || '',
            unit_label: '',
            unit_price: 0,
            default_checked: false,
            image_url: '',
          };
        } else {
          state.rows[idx] = {
            type: 'quantity',
            key: state.rows[idx].key || 'q_' + idx,
            title: state.rows[idx].title || '',
            unit_label: '',
            unit_price: 0,
            min: 0,
            max: 99,
            default_qty: 0,
            image_url: '',
          };
        }
        refresh();
      });

      head.querySelector('.ch-tm-up').addEventListener('click', function () {
        if (idx <= 0) return;
        var tmp = state.rows[idx - 1];
        state.rows[idx - 1] = state.rows[idx];
        state.rows[idx] = tmp;
        rerender();
      });
      head.querySelector('.ch-tm-down').addEventListener('click', function () {
        if (idx >= state.rows.length - 1) return;
        var tmp = state.rows[idx + 1];
        state.rows[idx + 1] = state.rows[idx];
        state.rows[idx] = tmp;
        rerender();
      });
      head.querySelector('.ch-tm-del').addEventListener('click', function () {
        state.rows.splice(idx, 1);
        rerender();
      });

      wireFields();
      list.appendChild(card);
    });
    root.appendChild(list);
  }

  global.CHInitTaskMenuEditor = function (opts) {
    var hiddenEl = document.querySelector(opts.hiddenSelector || '#task_menu_json_hidden');
    var rawEl = opts.rawSelector ? document.querySelector(opts.rawSelector) : null;
    var root = document.querySelector(opts.rootSelector || '#task-menu-editor-root');
    if (!hiddenEl || !root || !opts.uploadUrl) return;

    var state = parseInitial(hiddenEl.value);
    if (!state.banner_image_url) state.banner_image_url = '';

    function renderAllLocal() {
      root.innerHTML = '';
      renderBanner(root, state, opts, hiddenEl, rawEl);
      var addBar = document.createElement('div');
      addBar.className = 'd-flex flex-wrap gap-2 mb-3';
      addBar.innerHTML =
        '<span class="ch-field-label mb-0 align-self-center">Line items</span>' +
        '<button type="button" class="btn btn-sm btn-outline-secondary ch-tm-add-sec">+ Section</button>' +
        '<button type="button" class="btn btn-sm btn-outline-secondary ch-tm-add-qty">+ Quantity</button>' +
        '<button type="button" class="btn btn-sm btn-outline-secondary ch-tm-add-tog">+ Toggle</button>';
      root.appendChild(addBar);
      addBar.querySelector('.ch-tm-add-sec').addEventListener('click', function () {
        state.rows.push({ type: 'section', title: 'New section', subtitle: '' });
        renderAllLocal();
        syncHidden(state, hiddenEl, rawEl);
      });
      addBar.querySelector('.ch-tm-add-qty').addEventListener('click', function () {
        state.rows.push({
          type: 'quantity',
          key: 'item_' + state.rows.length,
          title: 'New line item',
          unit_label: 'units',
          unit_price: 0,
          min: 0,
          max: 99,
          default_qty: 0,
          image_url: '',
        });
        renderAllLocal();
        syncHidden(state, hiddenEl, rawEl);
      });
      addBar.querySelector('.ch-tm-add-tog').addEventListener('click', function () {
        state.rows.push({
          type: 'toggle',
          key: 'addon_' + state.rows.length,
          title: 'Add-on',
          unit_label: '',
          unit_price: 0,
          default_checked: false,
          image_url: '',
        });
        renderAllLocal();
        syncHidden(state, hiddenEl, rawEl);
      });
      renderRows(root, state, opts, hiddenEl, rawEl, function () {
        renderAllLocal();
        syncHidden(state, hiddenEl, rawEl);
      });
    }

    if (rawEl) {
      rawEl.addEventListener('input', function () {
        try {
          var o = JSON.parse(rawEl.value || '{}');
          if (o && typeof o === 'object' && Array.isArray(o.rows)) {
            state = o;
            if (!state.banner_image_url) state.banner_image_url = '';
            renderAllLocal();
            syncHidden(state, hiddenEl, rawEl);
          }
        } catch (e) {}
      });
    }

    var form = hiddenEl.closest('form');
    if (form) {
      form.addEventListener('submit', function () {
        /* refresh rows from DOM in case last edit not synced */
        var bu = root.querySelector('.ch-tm-banner-url');
        if (bu) state.banner_image_url = (bu.value || '').trim();
        var cards = root.querySelectorAll('.ch-tm-rows > div');
        var newRows = [];
        cards.forEach(function (card, i) {
          var sel = card.querySelector('.ch-tm-type');
          if (!sel) return;
          newRows.push(readRowFromCard(card, sel.value));
        });
        if (newRows.length) state.rows = newRows;
        syncHidden(state, hiddenEl, rawEl);
      });
    }

    renderAllLocal();
    syncHidden(state, hiddenEl, rawEl);
  };
})(typeof window !== 'undefined' ? window : this);
