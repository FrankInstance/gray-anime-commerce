import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';

if (process.argv.includes('--help')) {
  console.log('Usage: SMOKE_BASE_URL=https://demo.example.com SMOKE_ALLOW_WRITE=true SMOKE_EXPECT_DEMO_PAYMENT=true npm run smoke:demo');
  process.exit(0);
}

const baseUrl = process.env.SMOKE_BASE_URL?.replace(/\/$/, '');
const allowHttp = process.env.SMOKE_ALLOW_HTTP === 'true';
if (!baseUrl) throw new Error('SMOKE_BASE_URL is required.');
if (!allowHttp && !baseUrl.startsWith('https://')) {
  throw new Error('SMOKE_BASE_URL must use HTTPS unless SMOKE_ALLOW_HTTP=true.');
}
if (process.env.SMOKE_ALLOW_WRITE !== 'true') {
  throw new Error('SMOKE_ALLOW_WRITE=true is required because this check creates disposable demo data.');
}
if (process.env.SMOKE_EXPECT_DEMO_PAYMENT !== 'true') {
  throw new Error('SMOKE_EXPECT_DEMO_PAYMENT=true is required before confirming a simulated payment.');
}

class SmokeApiError extends Error {
  constructor(path, status, payload) {
    super(`${path} failed with HTTP ${status}: ${payload?.code ?? 'UNKNOWN'}`);
    this.status = status;
    this.payload = payload;
  }
}

async function request(path, { method = 'GET', token, cookie, body } = {}) {
  const traceId = randomBytes(12).toString('hex');
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    signal: AbortSignal.timeout(20_000),
    headers: {
      'X-Trace-Id': traceId,
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(cookie ? { Cookie: cookie } : {}),
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' })
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  assert.equal(response.headers.get('x-trace-id'), traceId, `${path} must preserve X-Trace-Id`);
  if (!response.ok) throw new SmokeApiError(path, response.status, payload);
  assert.equal(payload?.code, 'OK', `${path} must return an OK envelope`);
  return { data: payload.data, setCookie: response.headers.get('set-cookie') };
}

function cookieValue(setCookie) {
  assert.match(setCookie ?? '', /^gray_refresh=/, 'authentication response must set the refresh cookie');
  return setCookie.split(';', 1)[0];
}

function phase(message) {
  console.log(`[smoke] ${message}`);
}

const suffix = `${Date.now()}-${randomBytes(4).toString('hex')}`;
const account = {
  email: `deployment-smoke-${suffix}@example.com`,
  username: `smoke${suffix.replace(/\D/g, '').slice(-10)}`,
  password: 'GraySmoke123!'
};

phase('checking public catalog');
const works = await request('/api/v1/works?page=1&size=1&type=NOVEL');
assert.ok(Array.isArray(works.data.items) && works.data.items.length > 0, 'demo catalog must contain a novel');

phase('registering disposable account');
const registered = await request('/api/v1/auth/register', { method: 'POST', body: account });
assert.equal(registered.data.profile.email, account.email);
let refreshCookie = cookieValue(registered.setCookie);

phase('checking profile and refresh rotation');
const profile = await request('/api/v1/users/me', { token: registered.data.accessToken });
assert.equal(profile.data.email, account.email);
const refreshed = await request('/api/v1/auth/refresh', { method: 'POST', cookie: refreshCookie });
refreshCookie = cookieValue(refreshed.setCookie);
assert.ok(refreshed.data.accessToken);

phase('checking logout and login');
await request('/api/v1/auth/logout', { method: 'POST', cookie: refreshCookie });
const loggedIn = await request('/api/v1/auth/login', {
  method: 'POST',
  body: { email: account.email, password: account.password }
});
const token = loggedIn.data.accessToken;

phase('creating and confirming demo payment');
const order = await request('/api/v1/orders/points', {
  method: 'POST',
  token,
  body: { amountCents: 1000 }
});
assert.equal(order.data.status, 'PENDING_PAYMENT');
const checkout = await request('/api/v1/payments/checkout-session', {
  method: 'POST',
  token,
  body: { paymentNo: order.data.paymentNo }
});
assert.equal(checkout.data.provider, 'DEMO');
assert.equal(checkout.data.interactionMode, 'DEMO_CONFIRMATION');
const payment = await request(`/api/v1/payments/${order.data.paymentNo}/demo-confirm`, { method: 'POST', token });
assert.equal(payment.data.status, 'CONFIRMED');

phase('verifying settled account state');
const finalProfile = await request('/api/v1/users/me', { token });
assert.equal(finalProfile.data.points, 100);
console.log('[smoke] deployment smoke test passed');
