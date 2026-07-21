// Test session helper.
//
// Real Google SSO cannot be automated, so — exactly like the existing Java e2e test
// (ExperimentApiE2ETest) — the authenticated browser tests mint the very same session
// cookie the OAuth success handler would issue: an HS256 JWT signed with the app's
// JWT_SIGNING_SECRET, carrying the seeded user's id, tenant id and email.
//
// The user/tenant ids are read from the real MySQL the app uses, so the token matches
// an actual invited user rather than a guessed id.
const path = require('path');
const dotenv = require('dotenv');
const mysql = require('mysql2/promise');
const { SignJWT } = require('jose');

// Secrets live in the repo-root .env (same file the app and docker-compose read).
dotenv.config({ path: path.resolve(__dirname, '../../.env') });

const COOKIE_NAME = 'session'; // app.security.session-cookie-name

async function seededUser(tenantName, email) {
  const connection = await mysql.createConnection({
    host: process.env.MYSQL_HOST || '127.0.0.1',
    port: Number(process.env.MYSQL_PORT || 3306),
    user: process.env.MYSQL_USER || 'root',
    password: process.env.MYSQL_PASSWORD,
    database: process.env.MYSQL_DATABASE || 'visitorflow'
  });
  try {
    const [tenants] = await connection.execute('SELECT id FROM tenants WHERE name = ?', [tenantName]);
    if (tenants.length === 0) { throw new Error(`Tenant "${tenantName}" is not seeded`); }
    const tenantId = tenants[0].id;
    const [users] = await connection.execute(
      'SELECT id FROM users WHERE email = ? AND tenant_id = ?', [email, tenantId]
    );
    if (users.length === 0) { throw new Error(`User "${email}" is not invited to "${tenantName}"`); }
    return { tenantId, userId: users[0].id, email };
  } finally {
    await connection.end();
  }
}

async function mintSessionToken({ userId, tenantId, email }) {
  const secret = process.env.JWT_SIGNING_SECRET;
  if (!secret) { throw new Error('JWT_SIGNING_SECRET is not set (check .env)'); }
  const now = Math.floor(Date.now() / 1000);
  return new SignJWT({ tenant_id: Number(tenantId), email })
    .setProtectedHeader({ alg: 'HS256' })
    .setIssuer('demo')
    .setSubject(String(userId))
    .setIssuedAt(now)
    .setExpirationTime(now + 30 * 60)
    .sign(new TextEncoder().encode(secret));
}

// Builds the Playwright cookie the browser context should carry to be "logged in".
async function sessionCookie(baseURL, tenantName, email) {
  const user = await seededUser(tenantName, email);
  const token = await mintSessionToken(user);
  return { name: COOKIE_NAME, value: token, url: baseURL };
}

module.exports = { seededUser, mintSessionToken, sessionCookie, COOKIE_NAME };
