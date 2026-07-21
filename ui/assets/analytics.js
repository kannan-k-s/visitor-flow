// Analytics page controller: pick an experiment, show its per-variant results.
// The experiment can be preselected via ?experiment=<id> (linked from the list page).
(function () {
  const banner = document.querySelector('[data-banner]');
  const select = document.querySelector('[data-experiment-select]');
  const refreshBtn = document.querySelector('[data-refresh]');
  const resultsCard = document.querySelector('[data-results-card]');
  const emptyState = document.querySelector('[data-empty-state]');
  const resultsTitle = document.querySelector('[data-results-title]');
  const resultsTable = document.querySelector('[data-results-table]');
  const statId = document.querySelector('[data-stat-id]');
  const statOrphan = document.querySelector('[data-stat-orphan]');

  document.querySelector('[data-tenant-label]').textContent = Api.tenant;
  document.querySelector('[data-nav-experiments]').href = Api.experimentsPath;
  document.querySelector('[data-nav-analytics]').href = Api.analyticsPath;

  const nameById = {};

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
  function showError(message) { banner.textContent = message; banner.classList.add('show'); }
  function clearError() { banner.classList.remove('show'); }

  // conversion_rate is a decimal string like "0.2500" -> render as a percentage.
  function ratePct(rate) {
    const value = parseFloat(rate);
    if (isNaN(value)) { return '0.00%'; }
    return (value * 100).toFixed(2) + '%';
  }

  function renderResults(results) {
    statId.textContent = results.experiment_id;
    statOrphan.textContent = results.orphan_converted;
    const variants = results.variants || [];
    if (variants.length === 0) {
      resultsTable.innerHTML = '<div class="empty">No variant results yet.</div>';
      return;
    }
    let html = '<table><thead><tr>' +
      '<th>Variant ID</th><th>Assigned</th><th>Exposed</th><th>Converted</th>' +
      '<th>Conversion rate</th></tr></thead><tbody>';
    variants.forEach(function (v) {
      const pct = ratePct(v.conversion_rate);
      const width = Math.min(100, Math.max(0, parseFloat(v.conversion_rate) * 100 || 0));
      html += '<tr>' +
        '<td class="mono">' + escapeHtml(v.variant_id) + '</td>' +
        '<td>' + escapeHtml(v.assigned) + '</td>' +
        '<td>' + escapeHtml(v.exposed) + '</td>' +
        '<td>' + escapeHtml(v.converted) + '</td>' +
        '<td><div class="btn-row"><div class="bar"><span style="width:' + width + '%"></span></div>' +
          '<span>' + pct + '</span></div></td>' +
        '</tr>';
    });
    html += '</tbody></table>';
    resultsTable.innerHTML = html;
  }

  function loadResults(id) {
    if (!id) { return; }
    clearError();
    resultsCard.hidden = false;
    resultsTitle.textContent = 'Results — ' + (nameById[id] || ('#' + id));
    resultsTable.innerHTML = '<div class="empty">Loading…</div>';
    Api.getResults(id).then(renderResults).catch(function (error) {
      showError(error.message || 'Could not load results.');
      resultsTable.innerHTML = '<div class="empty">Failed to load results.</div>';
    });
  }

  function syncUrl(id) {
    const url = new URL(location.href);
    url.searchParams.set('experiment', id);
    history.replaceState(null, '', url);
  }

  select.addEventListener('change', function () {
    syncUrl(select.value);
    loadResults(select.value);
  });
  refreshBtn.addEventListener('click', function () { loadResults(select.value); });

  // Load enough experiments to populate the selector, then pick the requested one.
  Api.listExperiments(0, 100).then(function (pageData) {
    const items = pageData.items || [];
    if (items.length === 0) {
      emptyState.hidden = false;
      select.disabled = true;
      refreshBtn.disabled = true;
      return;
    }
    select.innerHTML = items.map(function (item) {
      nameById[item.id] = item.name;
      return '<option value="' + escapeHtml(item.id) + '">' +
        escapeHtml(item.name) + ' (#' + escapeHtml(item.id) + ')</option>';
    }).join('');
    const requested = new URLSearchParams(location.search).get('experiment');
    const initial = requested && nameById[requested] ? requested : items[0].id;
    select.value = initial;
    syncUrl(initial);
    loadResults(initial);
  }).catch(function (error) {
    showError(error.message || 'Could not load experiments.');
  });
})();
