// Experiments page controller: list (with paging), create, edit/update, inspect
// variants, delete. Every value the API returns is a JSON string (the DTOs use
// JsonFormat.Shape.STRING), so ids stay strings and numbers are parsed only where
// arithmetic is needed.
(function () {
  const banner = document.querySelector('[data-banner]');
  const notice = document.querySelector('[data-notice]');
  const listEl = document.querySelector('[data-list]');
  const pager = document.querySelector('[data-pager]');
  const pageInfo = document.querySelector('[data-page-info]');
  const prevBtn = document.querySelector('[data-prev]');
  const nextBtn = document.querySelector('[data-next]');
  const form = document.querySelector('[data-create-form]');
  const formTitle = document.querySelector('[data-form-title]');
  const variantRows = document.querySelector('[data-variant-rows]');
  const allocTotal = document.querySelector('[data-alloc-total]');
  const addVariantBtn = document.querySelector('[data-add-variant]');
  const submitBtn = document.querySelector('[data-submit]');
  const cancelBtn = document.querySelector('[data-cancel-edit]');

  const SIZE = 20;
  let page = 0;
  let totalPages = 1;
  let editingId = null; // null => create mode, otherwise the experiment id being edited

  document.querySelector('[data-tenant-label]').textContent = Api.tenant;
  document.querySelector('[data-nav-experiments]').href = Api.experimentsPath;
  document.querySelector('[data-nav-analytics]').href = Api.analyticsPath;

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
  function showError(message) {
    banner.textContent = message; banner.classList.add('show');
    notice.classList.remove('show');
  }
  function showNotice(message) {
    notice.textContent = message; notice.classList.add('show');
    banner.classList.remove('show');
  }
  function clearBanners() { banner.classList.remove('show'); notice.classList.remove('show'); }

  // ---- shared create/edit form: variant rows ----
  // On edit, existing variants keep their id (row.dataset.variantId) so the PUT
  // updates them in place; rows added without an id become new variants.
  function variantRow(content, alloc, isDefault, variantId) {
    const row = document.createElement('div');
    row.className = 'variant-row';
    if (variantId != null && variantId !== '') { row.dataset.variantId = variantId; }
    row.innerHTML =
      '<input class="v-content" placeholder="Variant content" value="' + escapeHtml(content || '') + '" />' +
      '<input class="v-alloc" type="number" min="0.01" max="100" step="0.01" placeholder="%" value="' +
        escapeHtml(alloc == null ? '' : alloc) + '" />' +
      '<div class="default-cell"><input class="v-default" type="radio" name="default-variant"' +
        (isDefault ? ' checked' : '') + ' title="Default variant" /></div>' +
      '<button type="button" class="ghost v-remove" title="Remove">✕</button>';
    row.querySelector('.v-remove').addEventListener('click', function () {
      row.remove(); renderAllocTotal();
    });
    row.querySelector('.v-alloc').addEventListener('input', renderAllocTotal);
    return row;
  }
  function renderAllocTotal() {
    let sum = 0;
    variantRows.querySelectorAll('.v-alloc').forEach(function (input) {
      const value = parseFloat(input.value);
      if (!isNaN(value)) { sum += value; }
    });
    const rounded = Math.round(sum * 100) / 100;
    allocTotal.textContent = 'Allocation total: ' + rounded + '%';
    allocTotal.classList.toggle('bad', rounded !== 100);
  }
  function seedDefaultVariants() {
    variantRows.innerHTML = '';
    variantRows.appendChild(variantRow('control', 50, true));
    variantRows.appendChild(variantRow('treatment', 50, false));
    renderAllocTotal();
  }
  addVariantBtn.addEventListener('click', function () {
    variantRows.appendChild(variantRow('', '', false));
    renderAllocTotal();
  });

  function collectVariants() {
    const variants = [];
    variantRows.querySelectorAll('.variant-row').forEach(function (row) {
      const variant = {
        content: row.querySelector('.v-content').value.trim(),
        alloc_pct: row.querySelector('.v-alloc').value.trim() === ''
          ? null : Number(row.querySelector('.v-alloc').value.trim()),
        is_default: row.querySelector('.v-default').checked
      };
      if (row.dataset.variantId) { variant.id = Number(row.dataset.variantId); }
      variants.push(variant);
    });
    return variants;
  }

  // ---- form mode ----
  function enterCreateMode() {
    editingId = null;
    form.reset();
    seedDefaultVariants();
    formTitle.textContent = 'Create experiment';
    submitBtn.textContent = 'Create experiment';
    cancelBtn.hidden = true;
  }
  function enterEditMode(exp) {
    editingId = exp.id;
    form.elements['name'].value = exp.name;
    form.elements['strategy'].value = exp.strategy;
    variantRows.innerHTML = '';
    (exp.variants || []).forEach(function (v) {
      variantRows.appendChild(variantRow(v.content, v.alloc_pct, v.is_default, v.id));
    });
    renderAllocTotal();
    formTitle.textContent = 'Edit experiment #' + exp.id;
    submitBtn.textContent = 'Save changes';
    cancelBtn.hidden = false;
    formTitle.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
  cancelBtn.addEventListener('click', function () { clearBanners(); enterCreateMode(); });

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    clearBanners();
    const payload = {
      name: form.elements['name'].value.trim(),
      strategy: form.elements['strategy'].value,
      variants: collectVariants()
    };
    submitBtn.disabled = true;
    const editing = editingId;
    const action = editing
      ? Api.updateExperiment(editing, payload)
      : Api.createExperiment(payload);
    action.then(function (saved) {
      showNotice((editing ? 'Updated' : 'Created') +
        ' experiment "' + saved.name + '" (#' + saved.id + ').');
      enterCreateMode();
      if (!editing) { page = 0; }
      return load();
    }).catch(function (error) {
      showError(error.message || ('Could not ' + (editing ? 'update' : 'create') + ' the experiment.'));
    }).finally(function () { submitBtn.disabled = false; });
  });

  // ---- list ----
  function strategyPill(strategy) {
    const safe = escapeHtml(strategy);
    return '<span class="pill ' + safe + '">' + safe + '</span>';
  }

  function renderList(pageData) {
    const items = pageData.items || [];
    totalPages = Math.max(1, parseInt(pageData.total_pages, 10) || 1);
    if (items.length === 0) {
      listEl.innerHTML = '<div class="empty">No experiments yet. Create one above.</div>';
      pager.hidden = true;
      return;
    }
    let html = '<table><thead><tr>' +
      '<th>ID</th><th>Name</th><th>Strategy</th><th></th>' +
      '</tr></thead><tbody>';
    items.forEach(function (item) {
      html += '<tr data-exp-id="' + escapeHtml(item.id) + '">' +
        '<td class="mono">' + escapeHtml(item.id) + '</td>' +
        '<td>' + escapeHtml(item.name) + '</td>' +
        '<td>' + strategyPill(item.strategy) + '</td>' +
        '<td><div class="btn-row" style="justify-content:flex-end">' +
          '<button class="secondary" data-edit="' + escapeHtml(item.id) + '">Edit</button>' +
          '<button class="secondary" data-view="' + escapeHtml(item.id) + '">Variants</button>' +
          '<a class="ghost" style="padding:9px 16px;border-radius:8px;border:1px solid var(--border)" ' +
            'data-analytics="' + escapeHtml(item.id) + '" ' +
            'href="' + Api.analyticsPath + '?experiment=' + encodeURIComponent(item.id) + '">Analytics</a>' +
          '<button class="danger" data-delete="' + escapeHtml(item.id) + '">Delete</button>' +
        '</div></td></tr>' +
        '<tr class="detail-row" data-detail="' + escapeHtml(item.id) + '" hidden><td colspan="4"></td></tr>';
    });
    html += '</tbody></table>';
    listEl.innerHTML = html;
    pager.hidden = totalPages <= 1;
    pageInfo.textContent = 'Page ' + (page + 1) + ' of ' + totalPages +
      ' · ' + pageData.total_elements + ' total';
    prevBtn.disabled = page <= 0;
    nextBtn.disabled = page >= totalPages - 1;
    wireRowActions();
  }

  function wireRowActions() {
    listEl.querySelectorAll('[data-edit]').forEach(function (button) {
      button.addEventListener('click', function () { startEdit(button.getAttribute('data-edit')); });
    });
    listEl.querySelectorAll('[data-view]').forEach(function (button) {
      button.addEventListener('click', function () { toggleVariants(button.getAttribute('data-view')); });
    });
    listEl.querySelectorAll('[data-delete]').forEach(function (button) {
      button.addEventListener('click', function () { remove(button.getAttribute('data-delete')); });
    });
  }

  function startEdit(id) {
    clearBanners();
    Api.getExperiment(id).then(enterEditMode).catch(function (error) {
      showError(error.message || 'Could not load the experiment for editing.');
    });
  }

  function toggleVariants(id) {
    const detail = listEl.querySelector('[data-detail="' + id + '"]');
    const cell = detail.querySelector('td');
    if (!detail.hidden) { detail.hidden = true; return; }
    cell.innerHTML = '<span class="muted">Loading variants…</span>';
    detail.hidden = false;
    Api.getExperiment(id).then(function (exp) {
      let rows = (exp.variants || []).map(function (v) {
        return '<tr><td class="mono">' + escapeHtml(v.id) + '</td>' +
          '<td>' + escapeHtml(v.content) + '</td>' +
          '<td>' + escapeHtml(v.alloc_pct) + '%</td>' +
          '<td>' + (v.is_default ? 'default' : '<span class="muted">—</span>') + '</td></tr>';
      }).join('');
      cell.innerHTML = '<table><thead><tr><th>Variant ID</th><th>Content</th>' +
        '<th>Allocation</th><th>Default</th></tr></thead><tbody>' + rows + '</tbody></table>';
    }).catch(function (error) {
      cell.innerHTML = '<span class="muted">Could not load variants: ' + escapeHtml(error.message) + '</span>';
    });
  }

  function remove(id) {
    if (!window.confirm('Delete experiment #' + id + '? This cannot be undone.')) { return; }
    clearBanners();
    if (String(editingId) === String(id)) { enterCreateMode(); }
    Api.deleteExperiment(id).then(function (result) {
      showNotice('Deleted experiment #' + result.experiment_id + '.');
      if (listEl.querySelectorAll('tr[data-exp-id]').length === 1 && page > 0) { page -= 1; }
      return load();
    }).catch(function (error) { showError(error.message || 'Could not delete the experiment.'); });
  }

  function load() {
    listEl.setAttribute('aria-busy', 'true');
    return Api.listExperiments(page, SIZE).then(renderList).catch(function (error) {
      showError(error.message || 'Could not load experiments.');
      listEl.innerHTML = '<div class="empty">Failed to load experiments.</div>';
    }).finally(function () { listEl.removeAttribute('aria-busy'); });
  }

  prevBtn.addEventListener('click', function () { if (page > 0) { page -= 1; load(); } });
  nextBtn.addEventListener('click', function () { if (page < totalPages - 1) { page += 1; load(); } });

  seedDefaultVariants();
  load();
})();
