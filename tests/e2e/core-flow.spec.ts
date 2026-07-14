import { expect, test, type APIRequestContext } from '@playwright/test';

const API_BASE_URL = process.env.CORE_API_BASE_URL ?? 'http://127.0.0.1:8080';
const AUTH_STORAGE_KEY = 'gray-shelf-auth-v1';

type Profile = {
  id: number;
  email: string;
  username: string;
  roles: string[];
  points: number;
  vipUntil: string | null;
};

type AuthResponse = {
  accessToken: string;
  expiresIn: number;
  profile: Profile;
};

type ApiEnvelope<T> = {
  code: string;
  message: string;
  data: T;
};

function uniqueAccount(prefix: string) {
  const suffix = `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`;
  return {
    email: `${prefix}-${suffix}@example.com`,
    username: `${prefix}${suffix.replace(/\D/g, '').slice(-8)}`.slice(0, 30),
    password: 'GrayTest123!'
  };
}

async function register(request: APIRequestContext, prefix: string) {
  const account = uniqueAccount(prefix);
  const response = await request.post(`${API_BASE_URL}/api/v1/auth/register`, { data: account });
  expect(response.ok()).toBeTruthy();
  const envelope = await response.json() as ApiEnvelope<AuthResponse>;
  expect(envelope.code).toBe('OK');
  return { account, auth: envelope.data };
}

test('registration persists the signed-in account after a page reload', async ({ page }) => {
  const account = uniqueAccount('browser-auth');
  await page.goto('/');

  await page.locator('.accountTrigger').click();
  await page.locator('.accountDropdown').getByRole('menuitem', { name: '注册' }).click();
  const dialog = page.getByRole('dialog', { name: '注册' });
  await dialog.getByLabel('邮箱').fill(account.email);
  await dialog.getByLabel('昵称').fill(account.username);
  await dialog.getByLabel('密码').fill(account.password);
  await dialog.getByRole('button', { name: '注册', exact: true }).click();

  await expect(dialog).toBeHidden();
  await expect(page.locator('.accountTrigger')).toContainText(account.username);

  await page.reload();
  await expect(page.locator('.accountTrigger')).toContainText(account.username);
  const storedAuth = await page.evaluate((key) => window.localStorage.getItem(key), AUTH_STORAGE_KEY);
  expect(storedAuth).toBeNull();
  const refreshCookie = (await page.context().cookies()).find((cookie) => cookie.name === 'gray_refresh');
  expect(refreshCookie?.httpOnly).toBe(true);
  expect(refreshCookie?.sameSite).toBe('Lax');
});

test('forgot password resets credentials and returns to login', async ({ page }) => {
  const { account } = await register(page.request, 'browser-reset');
  const logoutResponse = await page.request.post(`${API_BASE_URL}/api/v1/auth/logout`);
  expect(logoutResponse.ok()).toBeTruthy();
  await page.goto('/');

  await page.locator('.accountTrigger').click();
  await page.locator('.accountDropdown').getByRole('menuitem', { name: '登录' }).click();
  let dialog = page.getByRole('dialog', { name: '登录' });
  await dialog.getByRole('button', { name: '忘记密码' }).click();

  dialog = page.getByRole('dialog', { name: '找回密码' });
  await dialog.getByLabel('邮箱').fill(account.email);
  await dialog.getByRole('button', { name: '发送验证码' }).click();

  dialog = page.getByRole('dialog', { name: '重置密码' });
  await expect(dialog.getByLabel('验证码')).not.toHaveValue('');
  const newPassword = 'GrayReset456!';
  await dialog.getByLabel('新密码').fill(newPassword);
  await dialog.getByRole('button', { name: '重置密码' }).click();

  dialog = page.getByRole('dialog', { name: '登录' });
  await dialog.getByLabel('密码').fill(newPassword);
  await dialog.getByRole('button', { name: '登录', exact: true }).click();
  await expect(dialog).toBeHidden();
  await expect(page.locator('.accountTrigger')).toContainText(account.username);
});

test('cart selection controls checkout and demo payment completes the order', async ({ page, request }) => {
  const { auth } = await register(page.request, 'browser-cart');
  await page.goto('/');
  await expect(page.locator('.accountTrigger')).toContainText(auth.profile.username);

  await page.getByRole('navigation').getByRole('button', { name: '会员购' }).click();
  const productCard = page.locator('.productCard').filter({ hasText: '《星轨书店的魔女》实体书限定版' });
  await expect(productCard).toBeVisible();
  await productCard.getByRole('button', { name: '加入购物车' }).click();

  await expect(page.getByRole('status')).toHaveText('已加入购物车');
  await expect(page).toHaveURL(/\/$/);

  await page.locator('.cartTrigger').click();
  const cart = page.getByRole('dialog', { name: '购物车' });
  const checkout = cart.getByRole('button', { name: '结算', exact: true });
  const selectItem = cart.getByRole('button', { name: /取消选择 《星轨书店的魔女》实体书限定版/ });
  await expect(checkout).toBeEnabled();

  await selectItem.click();
  await expect(checkout).toBeDisabled();
  await cart.getByRole('button', { name: /选择 《星轨书店的魔女》实体书限定版/ }).click();
  await expect(checkout).toBeEnabled();

  await checkout.click();
  await expect(cart.getByText('订单已创建，请确认支付。')).toBeVisible();
  await cart.getByRole('button', { name: '模拟支付' }).click();

  await expect(cart).toBeHidden();
  await expect(page.getByRole('status')).toHaveText('支付成功');

  const ordersResponse = await request.get(`${API_BASE_URL}/api/v1/orders?page=1&size=20&status=PAID`, {
    headers: { Authorization: `Bearer ${auth.accessToken}` }
  });
  expect(ordersResponse.ok()).toBeTruthy();
  const orders = await ordersResponse.json() as ApiEnvelope<{ items: Array<{ status: string; paymentStatus: string }> }>;
  expect(orders.data.items.some((order) => order.status === 'PAID' && order.paymentStatus === 'CONFIRMED')).toBeTruthy();
});

test('reader scroll position is saved and restored after reload', async ({ page, request }) => {
  const { auth } = await register(page.request, 'browser-reader');
  await page.goto('/works/1/chapters/1/read');

  const article = page.locator('.readerArticle');
  await expect(article).toBeVisible();
  await expect(article).toContainText('夜班列车');
  await page.waitForTimeout(500);

  const requestedPercent = 62;
  const progressSaved = page.waitForResponse((response) => (
    response.request().method() === 'PUT'
      && response.url().endsWith('/api/v1/reading/progress')
  ));
  const actualPercent = await page.evaluate((percent) => {
    const target = document.querySelector<HTMLElement>('.readerArticle');
    if (!target) throw new Error('Reader article not found');
    document.documentElement.style.scrollBehavior = 'auto';
    const targetTop = target.getBoundingClientRect().top + window.scrollY;
    const scrollable = Math.max(0, target.scrollHeight - window.innerHeight);
    window.scrollTo({ top: targetTop + scrollable * percent / 100, behavior: 'auto' });
    window.dispatchEvent(new Event('scroll'));
    return Math.round(((window.scrollY - targetTop) / scrollable) * 100);
  }, requestedPercent);
  expect(actualPercent).toBeGreaterThanOrEqual(requestedPercent - 1);
  expect(actualPercent).toBeLessThanOrEqual(requestedPercent + 1);
  const saveResponse = await progressSaved;
  expect(saveResponse.ok()).toBeTruthy();

  const progressResponse = await request.get(`${API_BASE_URL}/api/v1/chapters/1/reader`, {
    headers: { Authorization: `Bearer ${auth.accessToken}` }
  });
  expect(progressResponse.ok()).toBeTruthy();
  const progressEnvelope = await progressResponse.json() as ApiEnvelope<{ progressPercent: number }>;
  expect(progressEnvelope.data.progressPercent).toBeGreaterThanOrEqual(requestedPercent - 3);
  expect(progressEnvelope.data.progressPercent).toBeLessThanOrEqual(requestedPercent + 3);

  await page.reload();
  await expect(article).toBeVisible();
  await page.waitForTimeout(700);

  const restoredPercent = await page.evaluate(() => {
    const target = document.querySelector<HTMLElement>('.readerArticle');
    if (!target) throw new Error('Reader article not found');
    const targetTop = target.getBoundingClientRect().top + window.scrollY;
    const scrollable = Math.max(0, target.scrollHeight - window.innerHeight);
    return Math.round(((window.scrollY - targetTop) / scrollable) * 100);
  });
  expect(restoredPercent).toBeGreaterThanOrEqual(progressEnvelope.data.progressPercent - 3);
  expect(restoredPercent).toBeLessThanOrEqual(progressEnvelope.data.progressPercent + 3);
});
