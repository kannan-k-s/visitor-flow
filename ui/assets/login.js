// Login page controller. The only real action is starting Google SSO; the backend
// owns the whole OAuth dance and mints the session cookie on success.
(function () {
  const tenantEl = document.querySelector('[data-tenant]');
  const banner = document.querySelector('[data-banner]');
  const button = document.querySelector('[data-google-signin]');

  tenantEl.textContent = Api.tenant || 'this workspace';
  document.title = 'Sign in · ' + (Api.tenant || 'Experiment Admin');

  // Surface an error passed back by the OAuth failure redirect (?error=...).
  const error = new URLSearchParams(location.search).get('error');
  if (error) {
    banner.textContent = error;
    banner.classList.add('show');
  }

  button.addEventListener('click', function () {
    button.disabled = true;
    Api.startGoogleLogin();
  });

  // If a valid session cookie is already present, skip the form entirely.
  Api.isAuthenticated().then(function (ok) {
    if (ok) { location.replace(Api.experimentsPath); }
  }).catch(function () { /* offline or backend down — stay on the login form */ });
})();
