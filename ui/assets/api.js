// Shared API client for the admin SPA.
//
// The tenant is the first path segment of the current URL (e.g. /demo/experiments
// -> "demo"), matching how the backend resolves the tenant from /{tenant}/v1/...
// (see AGENTS.md §3). nginx serves these pages on the same origin and proxies the
// versioned API, so the httpOnly session cookie rides along automatically.
(function (global) {
  const segments = location.pathname.split('/').filter(Boolean);
  const tenant = segments[0] || '';
  const apiBase = '/' + tenant + '/v1';

  class ApiError extends Error {
    constructor(status, code, message) {
      super(message);
      this.status = status;
      this.code = code;
    }
  }

  async function request(method, path, body) {
    const options = { method, headers: {}, credentials: 'same-origin' };
    if (body !== undefined) {
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }
    const response = await fetch(apiBase + path, options);
    if (response.status === 401) {
      // Session missing or expired — the control plane requires auth. Bounce to login.
      location.replace('/' + tenant + '/login');
      throw new ApiError(401, 'unauthorized', 'Authentication is required');
    }
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
      throw new ApiError(
        response.status,
        data && data.code ? data.code : 'error',
        data && data.message ? data.message : 'Request failed (' + response.status + ')'
      );
    }
    return data;
  }

  global.Api = {
    tenant: tenant,
    ApiError: ApiError,
    loginPath: '/' + tenant + '/login',
    experimentsPath: '/' + tenant + '/experiments',
    analyticsPath: '/' + tenant + '/analytics',

    // Kicks off Google SSO: the backend filter turns /{tenant}/v1/auth/login into a
    // 302 to Google's authorization endpoint and, on success, mints the session cookie.
    startGoogleLogin() { location.href = '/' + tenant + '/v1/auth/login'; },

    // A cookie-only probe used by the login page to skip the form when already signed in.
    async isAuthenticated() {
      const response = await fetch(apiBase + '/experiments?page=0&size=1', {
        credentials: 'same-origin'
      });
      return response.ok;
    },

    listExperiments(page, size) {
      const query = new URLSearchParams({ page: page || 0, size: size || 20 });
      return request('GET', '/experiments?' + query.toString());
    },
    getExperiment(id) { return request('GET', '/experiments/' + id); },
    createExperiment(payload) { return request('POST', '/experiments', payload); },
    updateExperiment(id, payload) { return request('PUT', '/experiments/' + id, payload); },
    deleteExperiment(id) { return request('DELETE', '/experiments/' + id); },
    getResults(id) { return request('GET', '/experiments/' + id + '/results'); }
  };
})(window);
