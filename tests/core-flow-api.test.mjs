import assert from 'node:assert/strict';
import test from 'node:test';

const API_BASE_URL = process.env.CORE_API_BASE_URL ?? 'http://127.0.0.1:8080';

class ApiError extends Error {
  constructor(status, body) {
    super(body?.message ?? `HTTP ${status}`);
    this.status = status;
    this.code = body?.code;
    this.body = body;
  }
}

async function requestApi(path, { method = 'GET', token, cookie, body, headers: extraHeaders = {} } = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      ...extraHeaders,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(cookie ? { Cookie: cookie } : {})
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  return { response, payload };
}

async function api(path, options = {}) {
  const { response, payload } = await requestApi(path, options);
  if (!response.ok) {
    throw new ApiError(response.status, payload);
  }
  assert.equal(payload?.code, 'OK', `${path} should return an OK envelope`);
  return payload.data;
}

function uniqueAccount(prefix) {
  const suffix = `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
  return {
    email: `${prefix}-${suffix}@example.com`,
    username: `${prefix}${suffix.replace(/\D/g, '').slice(-8)}`.slice(0, 30),
    password: 'GrayTest123!'
  };
}

function decodeJwtPart(part) {
  return JSON.parse(Buffer.from(part, 'base64url').toString('utf8'));
}

async function register(prefix) {
  const account = uniqueAccount(prefix);
  const auth = await api('/api/v1/auth/register', {
    method: 'POST',
    body: account
  });
  return { account, auth, token: auth.accessToken };
}

async function confirmDemoPayment(paymentNo, token) {
  const session = await api('/api/v1/payments/checkout-session', {
    method: 'POST',
    token,
    body: { paymentNo }
  });
  assert.equal(session.provider, 'DEMO');
  assert.equal(session.interactionMode, 'DEMO_CONFIRMATION');
  assert.equal(session.paymentNo, paymentNo);
  return api(`/api/v1/payments/${paymentNo}/demo-confirm`, { method: 'POST', token });
}

async function expectApiError(action, expectedCode, expectedMessage) {
  await assert.rejects(action, (error) => {
    assert.ok(error instanceof ApiError);
    assert.equal(error.code, expectedCode);
    if (expectedMessage) {
      assert.match(error.message, expectedMessage);
    }
    return true;
  });
}

test('a registered user can log in again and load their profile', async () => {
  const account = uniqueAccount('auth');
  const registered = await api('/api/v1/auth/register', {
    method: 'POST',
    body: account
  });

  assert.ok(registered.accessToken);
  assert.equal(registered.profile.email, account.email);
  assert.equal(registered.profile.points, 0);

  const loggedIn = await api('/api/v1/auth/login', {
    method: 'POST',
    body: { email: account.email, password: account.password }
  });
  const profile = await api('/api/v1/users/me', { token: loggedIn.accessToken });

  assert.equal(profile.id, registered.profile.id);
  assert.equal(profile.email, account.email);
  assert.deepEqual(profile.roles, ['USER']);
});

test('access tokens use RS256 claims and identity headers cannot impersonate a user', async () => {
  const { token } = await register('token-security');
  const parts = token.split('.');
  assert.equal(parts.length, 3);
  const header = decodeJwtPart(parts[0]);
  const claims = decodeJwtPart(parts[1]);

  assert.equal(header.alg, 'RS256');
  assert.equal(header.kid, 'gray-access-2026-01');
  assert.equal(claims.iss, 'gray-auth');
  assert.ok(claims.aud === 'gray-api' || (Array.isArray(claims.aud) && claims.aud.includes('gray-api')));
  assert.match(claims.jti, /^[0-9a-f-]{36}$/i);
  assert.ok(claims.iat <= claims.nbf && claims.nbf < claims.exp);

  const spoofed = await requestApi('/api/v1/users/me', {
    headers: {
      'X-User-Id': '1',
      'X-User-Roles': 'SUPER_ADMIN'
    }
  });
  assert.equal(spoofed.response.status, 401);
  assert.equal(spoofed.payload.code, 'UNAUTHORIZED');

  const internal = await requestApi('/api/v1/internal/inventory/skus/1');
  assert.equal(internal.response.status, 404);
});

test('refresh sessions rotate, survive reloads, and logout revokes them', async () => {
  const account = uniqueAccount('session');
  const registered = await requestApi('/api/v1/auth/register', {
    method: 'POST',
    body: account
  });
  assert.equal(registered.response.status, 200);
  const initialSetCookie = registered.response.headers.get('set-cookie');
  assert.match(initialSetCookie, /^gray_refresh=/);
  assert.match(initialSetCookie, /HttpOnly/i);
  assert.match(initialSetCookie, /Max-Age=259200/i);
  assert.match(initialSetCookie, /SameSite=Lax/i);
  const initialCookie = initialSetCookie.split(';', 1)[0];

  const refreshed = await requestApi('/api/v1/auth/refresh', {
    method: 'POST',
    cookie: initialCookie
  });
  assert.equal(refreshed.response.status, 200);
  assert.ok(refreshed.payload.data.accessToken);
  assert.equal(refreshed.payload.data.expiresIn, 900);
  const rotatedCookie = refreshed.response.headers.get('set-cookie').split(';', 1)[0];
  assert.notEqual(rotatedCookie, initialCookie);

  await expectApiError(
    () => api('/api/v1/auth/refresh', { method: 'POST', cookie: initialCookie }),
    'SESSION_EXPIRED'
  );

  await api('/api/v1/auth/logout', { method: 'POST', cookie: rotatedCookie });
  await expectApiError(
    () => api('/api/v1/auth/refresh', { method: 'POST', cookie: rotatedCookie }),
    'SESSION_EXPIRED'
  );
});

test('password reset revokes existing sessions and accepts only the new password', async () => {
  const account = uniqueAccount('password-reset');
  const registered = await requestApi('/api/v1/auth/register', {
    method: 'POST',
    body: account
  });
  const sessionCookie = registered.response.headers.get('set-cookie').split(';', 1)[0];

  const reset = await api('/api/v1/auth/password-reset/request', {
    method: 'POST',
    body: { email: account.email }
  });
  assert.match(reset.devToken, /^\d{6}$/);

  const newPassword = 'GrayReset456!';
  await api('/api/v1/auth/password-reset/confirm', {
    method: 'POST',
    body: { token: reset.devToken, newPassword }
  });

  await expectApiError(
    () => api('/api/v1/auth/refresh', { method: 'POST', cookie: sessionCookie }),
    'SESSION_EXPIRED'
  );
  await expectApiError(
    () => api('/api/v1/auth/login', {
      method: 'POST',
      body: { email: account.email, password: account.password }
    }),
    'BAD_CREDENTIALS'
  );
  const loggedIn = await api('/api/v1/auth/login', {
    method: 'POST',
    body: { email: account.email, password: newPassword }
  });
  assert.equal(loggedIn.profile.email, account.email);
});

test('reading progress restores the exact position and Chinese text stays UTF-8', async () => {
  const { token } = await register('reader');

  await api('/api/v1/works/1/bookshelf', { method: 'POST', token });
  const firstOpen = await api('/api/v1/chapters/1/reader', { token });
  assert.equal(firstOpen.unlocked, true);
  assert.match(firstOpen.text, /夜班列车/);
  assert.doesNotMatch(firstOpen.text, /å¤œ|闈㈠|锟斤拷|ï¿½/);

  const saved = await api('/api/v1/reading/progress', {
    method: 'PUT',
    token,
    body: { chapterId: 1, progressPercent: 48 }
  });
  assert.equal(saved.progressPercent, 48);

  const reopened = await api('/api/v1/chapters/1/reader', { token });
  assert.equal(reopened.progressPercent, 48);

  const bookshelf = await api('/api/v1/reading/bookshelf?page=1&size=20', { token });
  const item = bookshelf.items.find((entry) => entry.workId === 1);
  assert.ok(item, 'saved work should appear on the bookshelf');
  assert.equal(item.lastChapterId, 1);
  assert.equal(item.progressPercent, 48);
});

test('points recharge unlocks a paid chapter through demo payment', async () => {
  const { token } = await register('points');

  await expectApiError(
    () => api('/api/v1/chapters/2/purchase', { method: 'POST', token }),
    'POINTS_NOT_ENOUGH'
  );

  const rechargeOrder = await api('/api/v1/orders/points', {
    method: 'POST',
    token,
    body: { amountCents: 1000 }
  });
  assert.equal(rechargeOrder.status, 'PENDING_PAYMENT');
  assert.equal(rechargeOrder.totalPoints, 100);
  assert.ok(rechargeOrder.paymentNo);

  const payment = await confirmDemoPayment(rechargeOrder.paymentNo, token);
  assert.equal(payment.status, 'CONFIRMED');

  const rechargedProfile = await api('/api/v1/users/me', { token });
  assert.equal(rechargedProfile.points, 100);

  const chapterOrder = await api('/api/v1/chapters/2/purchase', { method: 'POST', token });
  assert.equal(chapterOrder.status, 'PAID');
  assert.equal(chapterOrder.totalPoints, 20);

  const reader = await api('/api/v1/chapters/2/reader', { token });
  assert.equal(reader.unlocked, true);
  assert.match(reader.text, /星砂契约/);
  assert.doesNotMatch(reader.text, /å¤œ|闈㈠|锟斤拷|ï¿½/);

  const finalProfile = await api('/api/v1/users/me', { token });
  assert.equal(finalProfile.points, 80);
});

test('a product order can be paid and appears with the concrete product title', async () => {
  const { token } = await register('checkout');
  const products = await api('/api/v1/products?page=1&size=20', { token });
  const product = products.items.find((entry) => entry.id === 1);
  const sku = product?.skus[0];
  assert.ok(product && sku, 'seeded standard product should be available');

  const cartItem = await api('/api/v1/cart/items', {
    method: 'POST',
    token,
    body: { skuId: sku.id, quantity: 1 }
  });
  assert.equal(cartItem.skuId, sku.id);
  assert.equal(cartItem.quantity, 1);

  const order = await api('/api/v1/orders', {
    method: 'POST',
    token,
    body: { items: [{ skuId: sku.id, quantity: 1 }] }
  });
  assert.equal(order.status, 'PENDING_PAYMENT');
  assert.equal(order.totalCents, sku.priceCents);

  const payment = await confirmDemoPayment(order.paymentNo, token);
  assert.equal(payment.status, 'CONFIRMED');

  const paidOrders = await api('/api/v1/orders?page=1&size=20&status=PAID', { token });
  const paidOrder = paidOrders.items.find((entry) => entry.orderNo === order.orderNo);
  assert.ok(paidOrder, 'paid order should be returned by the PAID filter');
  assert.equal(paidOrder.paymentStatus, 'CONFIRMED');
  assert.match(paidOrder.items[0].title, new RegExp(product.title.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
});

test('a pending product order can be cancelled and can no longer be paid', async () => {
  const { token } = await register('cancel');
  const order = await api('/api/v1/orders', {
    method: 'POST',
    token,
    body: { items: [{ skuId: 1, quantity: 1 }] }
  });

  const cancelled = await api(`/api/v1/orders/${order.orderNo}/cancel`, {
    method: 'POST',
    token
  });
  assert.equal(cancelled.status, 'CANCELLED');
  assert.equal(cancelled.paymentStatus, 'CANCELLED');

  await expectApiError(
    () => confirmDemoPayment(order.paymentNo, token),
    'ORDER_CANCELLED'
  );
});

test('payment ownership is enforced and repeated demo confirmation is idempotent', async () => {
  const { token: ownerToken } = await register('payment-owner');
  const { token: attackerToken } = await register('payment-attacker');
  const order = await api('/api/v1/orders/points', {
    method: 'POST',
    token: ownerToken,
    body: { amountCents: 1000 }
  });

  await expectApiError(
    () => api(`/api/v1/payments/${order.paymentNo}/demo-confirm`, {
      method: 'POST',
      token: ownerToken
    }),
    'PAYMENT_SESSION_REQUIRED'
  );

  await expectApiError(
    () => api('/api/v1/payments/checkout-session', {
      method: 'POST',
      token: attackerToken,
      body: { paymentNo: order.paymentNo }
    }),
    'PAYMENT_NOT_FOUND'
  );
  await expectApiError(
    () => api(`/api/v1/payments/${order.paymentNo}/demo-confirm`, {
      method: 'POST',
      token: attackerToken
    }),
    'PAYMENT_NOT_FOUND'
  );

  await confirmDemoPayment(order.paymentNo, ownerToken);
  const repeated = await api(`/api/v1/payments/${order.paymentNo}/demo-confirm`, {
    method: 'POST',
    token: ownerToken
  });
  assert.equal(repeated.status, 'CONFIRMED');
  const profile = await api('/api/v1/users/me', { token: ownerToken });
  assert.equal(profile.points, 100);
});

test('VIP activation applies the member price to product checkout', async () => {
  const { token } = await register('vip');
  const vipOrder = await api('/api/v1/vip/orders', { method: 'POST', token });
  await confirmDemoPayment(vipOrder.paymentNo, token);

  const profile = await api('/api/v1/users/me', { token });
  assert.ok(profile.roles.includes('VIP'));
  assert.ok(profile.vipUntil);

  const product = await api('/api/v1/products/1', { token });
  const sku = product.skus[0];
  const order = await api('/api/v1/orders', {
    method: 'POST',
    token,
    body: { items: [{ skuId: sku.id, quantity: 1 }] }
  });
  assert.equal(order.totalCents, sku.vipPriceCents);
  assert.equal(order.items[0].unitPriceCents, sku.vipPriceCents);

  await api(`/api/v1/orders/${order.orderNo}/cancel`, { method: 'POST', token });
});

test('limited products return the purchase-limit error used by the cart UI', async () => {
  const { token } = await register('limited');

  await expectApiError(
    () => api('/api/v1/orders', {
      method: 'POST',
      token,
      body: { items: [{ skuId: 2, quantity: 2 }] }
    }),
    'PURCHASE_LIMIT_EXCEEDED',
    /限购商品，你已超出购买数量/
  );
});
