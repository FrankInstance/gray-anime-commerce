import {
  Bookmark,
  BookOpen,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Coins,
  Crown,
  Gift,
  Lock,
  LogIn,
  LogOut,
  Minus,
  Plus,
  Search,
  ShoppingBag,
  SlidersHorizontal,
  Sparkles,
  Ticket,
  Trash2,
  UserPlus,
  UserRound,
  X
} from 'lucide-react';
import { type CSSProperties, FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';

type ApiResponse<T> = { code: string; message: string; data: T; traceId: string };
type PageResult<T> = { items: T[]; page: number; size: number; total: number };
type Work = { id: number; title: string; workType: string; author: string; category: string; description: string; coverUrl: string; popularity: number };
type WorkDetail = { work: Work; chapters: Chapter[] };
type Chapter = { id: number; chapterNo: number; title: string; free: boolean; pricePoints: number; unlocked?: boolean; accessLabel?: string };
type ReaderResponse = { chapterId: number; title: string; unlocked: boolean; text: string; images: string[]; progressPercent: number };
type BookshelfItem = Work & {
  workId: number;
  lastChapterId: number | null;
  lastChapterNo: number | null;
  lastChapterTitle: string | null;
  progressPercent: number;
  addedAt: string;
  updatedAt: string;
};
type ReadingProgress = Work & {
  workId: number;
  chapterId: number;
  chapterNo: number;
  chapterTitle: string;
  progressPercent: number;
  updatedAt: string;
};
type Product = {
  id: number;
  title: string;
  productType: string;
  description: string;
  coverUrl: string;
  limited: boolean;
  saleStartAt: string | null;
  skus: { id: number; skuName: string; priceCents: number; vipPriceCents: number }[];
};
type ProductSku = Product['skus'][number];
type CartLine = {
  skuId: number;
  productId: number;
  title: string;
  productType: string;
  coverUrl: string;
  limited: boolean;
  skuName: string;
  originalPriceCents: number;
  vipPriceCents: number;
  quantity: number;
};
type Profile = { id: number; email: string; username: string; roles: string[]; status?: string; points: number; vipUntil: string | null };
type AuthMode = 'login' | 'register' | 'resetRequest' | 'resetConfirm';
type AuthResponse = { accessToken: string; expiresIn: number; profile: Profile; roles: string[] };
type PasswordResetResponse = { channel: string; devToken?: string | null; expiresAt: string };
type OrderItemView = { itemType: string; refId: number | null; skuId: number | null; title: string; quantity: number; unitPriceCents: number; unitPoints: number; reservationNo: string | null };
type OrderView = { id: number; orderNo: string; orderType: string; totalCents: number; totalPoints: number; status: string; paymentNo: string | null; paymentStatus: string | null; paymentChannel: string | null; paidAt: string | null; createdAt: string; items: OrderItemView[] };
type PointsLedgerView = { id: number; amount: number; reason: string; bizKey: string; createdAt: string };
type PaymentView = { paymentNo: string; orderNo: string; amountCents: number; channel: string; status: string; confirmedAt: string | null };
type PendingPayment = { orderNo: string; paymentNo: string; amountCents: number; status: string; itemSkuIds?: number[] };
type PendingRechargePayment = PendingPayment & { optionTitle: string; successMessage: string };
type RealPaymentRequest = { orderNo: string; paymentNo: string; amountCents: number };
type RealPaymentSession = {
  provider: string;
  sessionId: string;
  paymentNo: string;
  interactionMode: 'DEMO_CONFIRMATION' | 'REDIRECT';
  redirectUrl: string | null;
  expiresAt: string;
};
type RechargeOption = {
  id: string;
  type: 'POINTS' | 'VIP';
  title: string;
  amountCents: number;
  points?: number;
  caption: string;
};
type SectionKey = 'NOVEL' | 'MANGA' | 'MEMBER';
type AccountOrderFilter = 'ALL' | 'PENDING_PAYMENT' | 'PAID' | 'CANCELLED';
type BookshelfSort = 'recentReading' | 'recentAdded';
type ReaderTheme = 'night' | 'dim' | 'paper';

const DEMO_PAYMENT_ENABLED = import.meta.env.VITE_PAYMENT_MODE === 'demo';
type ReaderWidth = 'compact' | 'standard' | 'wide';
type ReaderSettings = {
  fontSize: number;
  lineHeight: number;
  theme: ReaderTheme;
  width: ReaderWidth;
};
type AppRoute =
  | { kind: 'list' }
  | { kind: 'account' }
  | { kind: 'accountBookshelf' }
  | { kind: 'accountOrders' }
  | { kind: 'work'; id: number }
  | { kind: 'reader'; workId: number; chapterId: number }
  | { kind: 'product'; id: number };

const PAGE_SIZE = 10;
const AUTH_STORAGE_KEY = 'gray-shelf-auth-v1';
const SESSION_KEEPALIVE_INTERVAL_MS = 10 * 60 * 1000;
const CART_STORAGE_KEY = 'gray-shelf-cart-v1';
const READER_SETTINGS_STORAGE_KEY = 'gray-shelf-reader-settings-v1';
const MAX_CART_QUANTITY = 99;
const PURCHASE_LIMIT_MESSAGE = '限购商品，你已超出购买数量';
const DEFAULT_READER_SETTINGS: ReaderSettings = {
  fontSize: 20,
  lineHeight: 2,
  theme: 'night',
  width: 'standard'
};
const readerWidthPx: Record<ReaderWidth, number> = {
  compact: 680,
  standard: 760,
  wide: 920
};

const sections: { key: SectionKey; label: string }[] = [
  { key: 'NOVEL', label: '轻小说' },
  { key: 'MANGA', label: '漫画' },
  { key: 'MEMBER', label: '会员购' }
];

const accountOrderFilters: { value: AccountOrderFilter; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' }
];

const bookshelfSortOptions: { value: BookshelfSort; label: string }[] = [
  { value: 'recentReading', label: '最近阅读' },
  { value: 'recentAdded', label: '最近加入' }
];

const readerThemeOptions: { value: ReaderTheme; label: string }[] = [
  { value: 'night', label: '夜色' },
  { value: 'dim', label: '低光' },
  { value: 'paper', label: '纸感' }
];

const readerWidthOptions: { value: ReaderWidth; label: string }[] = [
  { value: 'compact', label: '窄' },
  { value: 'standard', label: '标准' },
  { value: 'wide', label: '宽' }
];

const rechargeOptions: RechargeOption[] = [
  { id: 'points-10', type: 'POINTS', title: '100 积分', amountCents: 1000, points: 100, caption: '¥10 · 1元 = 10积分' },
  { id: 'points-50', type: 'POINTS', title: '500 积分', amountCents: 5000, points: 500, caption: '¥50 · 1元 = 10积分' },
  { id: 'points-100', type: 'POINTS', title: '1000 积分', amountCents: 10000, points: 1000, caption: '¥100 · 1元 = 10积分' },
  { id: 'vip-month', type: 'VIP', title: 'VIP 月卡', amountCents: 3000, caption: '¥30 · 免费阅读 VIP 章节' }
];

const fallbackWorks: Work[] = [
  {
    id: 1,
    title: '星轨书店的魔女',
    workType: 'NOVEL',
    author: 'Lumen Circle',
    category: 'Fantasy',
    description: '在废弃轨道尽头经营书店的魔女，收集被遗忘的故事碎片。',
    coverUrl: 'https://picsum.photos/seed/gray-novel-a/900/1200',
    popularity: 932
  },
  {
    id: 2,
    title: '雨港机械少女',
    workType: 'MANGA',
    author: 'North Pier',
    category: 'Sci-Fi',
    description: '机械少女在雨港寻找失踪设计师的漫画分镜演示。',
    coverUrl: 'https://picsum.photos/seed/gray-manga-a/900/1200',
    popularity: 884
  }
];

const fallbackProducts: Product[] = [
  {
    id: 1,
    title: '《星轨书店的魔女》实体书限定版',
    productType: 'BOOK',
    description: '首刷附星砂书签，VIP 9折。',
    coverUrl: 'https://picsum.photos/seed/gray-book-a/900/1200',
    limited: false,
    saleStartAt: null,
    skus: [{ id: 1, skuName: '限定版', priceCents: 6800, vipPriceCents: 6120 }]
  },
  {
    id: 2,
    title: '雨港机械少女 亚克力立牌',
    productType: 'GOODS',
    description: '限时开售，每人限购 1 件。',
    coverUrl: 'https://picsum.photos/seed/gray-stand-a/900/1200',
    limited: true,
    saleStartAt: new Date(Date.now() + 7200000).toISOString(),
    skus: [{ id: 2, skuName: '标准款', priceCents: 3900, vipPriceCents: 3510 }]
  }
];

const fallbackChapters: Chapter[] = [
  { id: 1, chapterNo: 1, title: '第一章 夜班列车', free: true, pricePoints: 0 },
  { id: 2, chapterNo: 2, title: '第二章 星砂契约', free: false, pricePoints: 20 }
];

const fallbackReaderText = [
  '列车驶过星轨，书页像薄雪一样翻飞。',
  '车窗外的废弃轨道铺向夜色深处，少女把书签夹进泛黄的书页，听见远处传来旧书店的铃声。',
  ...Array.from({ length: 20 }, (_, index) => (
    `第 ${index + 1} 段：车厢里的灯光随着铁轨轻轻晃动，窗外的霓虹从蓝色变成粉色，又被雨水拉成长长的线。魔女翻开没有封面的书，把散落的票根一张张贴回去。每贴好一张，座位上就会出现一段新的记忆：有人在末班车上告白，有人在旧书店门口道别，也有人只是安静地读完最后一页。`
  )),
  '列车抵达终点时，天色还没有亮。魔女合上书箱，铃铛轻轻响了一声。她知道，只要还有人愿意继续读下去，故事就不会真正结束。'
].join('\n\n');

class ApiRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code: string
  ) {
    super(message);
  }
}

async function api<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const response = await fetch(path, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers ?? {})
    }
  });
  if (!response.ok) {
    const text = await response.text();
    let message = text || response.statusText;
    let code = `HTTP_${response.status}`;
    try {
      const body = JSON.parse(text) as Partial<ApiResponse<unknown>>;
      message = body.message || message;
      code = body.code || code;
    } catch {
      // Keep non-JSON errors readable in forms and notices.
    }
    throw new ApiRequestError(message, response.status, code);
  }
  const body = (await response.json()) as ApiResponse<T>;
  return body.data;
}

async function executePayment(payment: RealPaymentRequest, token: string) {
  const session = await api<RealPaymentSession>('/api/v1/payments/checkout-session', {
    method: 'POST',
    body: JSON.stringify({ paymentNo: payment.paymentNo })
  }, token);

  if (DEMO_PAYMENT_ENABLED) {
    if (session.interactionMode !== 'DEMO_CONFIRMATION' || session.provider !== 'DEMO') {
      throw new Error('演示支付暂不可用。');
    }
    await api<PaymentView>(`/api/v1/payments/${session.paymentNo}/demo-confirm`, { method: 'POST' }, token);
    return true;
  }

  if (session.interactionMode !== 'REDIRECT' || !session.redirectUrl) {
    throw new Error('支付服务暂不可用。');
  }
  const redirectUrl = new URL(session.redirectUrl, window.location.origin);
  if (redirectUrl.protocol !== 'https:' && redirectUrl.protocol !== 'http:') {
    throw new Error('支付地址无效。');
  }
  window.location.assign(redirectUrl.toString());
  return false;
}

function money(cents: number) {
  return `¥${(cents / 100).toFixed(2)}`;
}

function signedPoints(points: number) {
  return `${points > 0 ? '+' : ''}${points}`;
}

function shortDateTime(value: string | null | undefined) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function dateValue(value: string | null | undefined) {
  if (!value) return 0;
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? 0 : time;
}

function clampNumber(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function normalizeProgressPercent(value: number | null | undefined) {
  return Math.round(clampNumber(Number.isFinite(value ?? NaN) ? Number(value) : 0, 0, 100));
}

function progressPercentText(value: number | null | undefined) {
  return `${normalizeProgressPercent(value)}%`;
}

function readerProgressPercent(target: HTMLElement | null) {
  if (typeof window === 'undefined' || !target) return 0;
  const targetTop = target.getBoundingClientRect().top + window.scrollY;
  const scrollable = Math.max(0, target.scrollHeight - window.innerHeight);
  if (scrollable <= 0) return 0;
  return normalizeProgressPercent(((window.scrollY - targetTop) / scrollable) * 100);
}

function readerScrollTopForPercent(target: HTMLElement | null, percent: number) {
  if (typeof window === 'undefined' || !target) return 0;
  const targetTop = target.getBoundingClientRect().top + window.scrollY;
  const scrollable = Math.max(0, target.scrollHeight - window.innerHeight);
  return Math.max(0, Math.round(targetTop + scrollable * normalizeProgressPercent(percent) / 100));
}

function sanitizeReaderSettings(settings: Partial<ReaderSettings>): ReaderSettings {
  const theme = (readerThemeOptions.some((option) => option.value === settings.theme) ? settings.theme : DEFAULT_READER_SETTINGS.theme) as ReaderTheme;
  const width = (readerWidthOptions.some((option) => option.value === settings.width) ? settings.width : DEFAULT_READER_SETTINGS.width) as ReaderWidth;
  const rawFontSize = typeof settings.fontSize === 'number' ? settings.fontSize : DEFAULT_READER_SETTINGS.fontSize;
  const rawLineHeight = typeof settings.lineHeight === 'number' ? settings.lineHeight : DEFAULT_READER_SETTINGS.lineHeight;
  return {
    fontSize: Math.round(clampNumber(rawFontSize, 16, 26)),
    lineHeight: Math.round(clampNumber(rawLineHeight, 1.6, 2.4) * 10) / 10,
    theme,
    width
  };
}

function loadStoredReaderSettings() {
  if (typeof window === 'undefined') return DEFAULT_READER_SETTINGS;
  try {
    const raw = window.localStorage.getItem(READER_SETTINGS_STORAGE_KEY);
    if (!raw) return DEFAULT_READER_SETTINGS;
    return sanitizeReaderSettings(JSON.parse(raw) as Partial<ReaderSettings>);
  } catch {
    return DEFAULT_READER_SETTINGS;
  }
}

function orderTypeLabel(type: string) {
  return ({
    PRODUCT: '会员购商品',
    VIP: 'VIP',
    POINTS: '积分充值',
    CHAPTER: '章节兑换'
  } as Record<string, string>)[type] ?? type;
}

function orderStatusLabel(status: string) {
  return ({
    PENDING_PAYMENT: '待支付',
    PAID: '已完成',
    CANCELLED: '已取消'
  } as Record<string, string>)[status] ?? status;
}

function paymentStatusLabel(status: string | null | undefined) {
  if (!status) return '无支付单';
  return ({
    PENDING: '待确认',
    CONFIRMED: '已确认',
    CANCELLED: '已取消'
  } as Record<string, string>)[status] ?? status;
}

function canPayOrder(order: Pick<OrderView, 'paymentNo' | 'status' | 'paymentStatus'>) {
  return Boolean(order.paymentNo)
    && order.status !== 'PAID'
    && order.status !== 'CANCELLED'
    && order.paymentStatus !== 'CONFIRMED'
    && order.paymentStatus !== 'CANCELLED';
}

function canCancelOrder(order: Pick<OrderView, 'status'>) {
  return order.status === 'PENDING_PAYMENT';
}

function chapterAccessLabel(chapter: Chapter) {
  return chapter.accessLabel || (chapter.free ? '免费' : `${chapter.pricePoints}积分`);
}

function chapterIsReadable(chapter: Chapter) {
  return chapter.unlocked ?? chapter.free;
}

function orderAmountText(order: Pick<OrderView, 'totalCents' | 'totalPoints'>) {
  if (order.totalCents > 0) return money(order.totalCents);
  if (order.totalPoints > 0) return `${order.totalPoints} 积分`;
  return '免费';
}

function orderItemTypeLabel(type: string) {
  return ({
    SKU: '商品',
    PRODUCT: '商品',
    VIP: '会员',
    POINTS: '积分',
    CHAPTER: '章节'
  } as Record<string, string>)[type] ?? type;
}

function orderItemUnitText(item: Pick<OrderItemView, 'unitPriceCents' | 'unitPoints'>) {
  if (item.unitPriceCents > 0) return money(item.unitPriceCents);
  if (item.unitPoints > 0) return `${item.unitPoints} 积分`;
  return '免费';
}

function orderItemSubtotalText(item: Pick<OrderItemView, 'unitPriceCents' | 'unitPoints' | 'quantity'>) {
  if (item.unitPriceCents > 0) return money(item.unitPriceCents * item.quantity);
  if (item.unitPoints > 0) return `${item.unitPoints * item.quantity} 积分`;
  return '免费';
}

function orderDisplayItems(order: OrderView) {
  if (order.items.length) return order.items;
  return [{
    itemType: order.orderType,
    refId: null,
    skuId: null,
    title: order.orderType === 'VIP' ? 'VIP 月卡' : orderTypeLabel(order.orderType),
    quantity: 1,
    unitPriceCents: order.totalCents,
    unitPoints: order.totalPoints,
    reservationNo: null
  }];
}

function ledgerReasonLabel(reason: string) {
  return ({
    SIGN_IN: '每日签到',
    POINTS_RECHARGE: '积分充值',
    CHAPTER_PURCHASE: '章节兑换'
  } as Record<string, string>)[reason] ?? reason;
}

function hasVipDiscount(priceCents: number, vipPriceCents: number) {
  return vipPriceCents > 0 && vipPriceCents < priceCents;
}

function discountRateLabel(priceCents: number, vipPriceCents: number) {
  if (!hasVipDiscount(priceCents, vipPriceCents)) return '';
  const rate = Math.round((vipPriceCents / priceCents) * 100) / 10;
  return `${Number.isInteger(rate) ? rate.toFixed(0) : rate.toFixed(1)}折`;
}

function vipDiscountText(priceCents: number, vipPriceCents: number) {
  if (!hasVipDiscount(priceCents, vipPriceCents)) return '';
  return `VIP ${discountRateLabel(priceCents, vipPriceCents)} · 省 ${money(priceCents - vipPriceCents)}`;
}

function cartStorageKey(profile: Profile | null) {
  return `${CART_STORAGE_KEY}:${profile ? `user:${profile.id}` : 'guest'}`;
}

function cartLineKey(line: Pick<CartLine, 'skuId'>) {
  return String(line.skuId);
}

function cartSkuIdSet(items: Pick<CartLine, 'skuId'>[]) {
  return new Set(items.map((item) => item.skuId));
}

function cartQuantityTotal(items: Pick<CartLine, 'quantity'>[]) {
  return items.reduce((total, item) => total + item.quantity, 0);
}

function cartQuantityLimit(line: Pick<CartLine, 'limited'>) {
  return line.limited ? 1 : MAX_CART_QUANTITY;
}

function normalizeQuantity(quantity: number, line: Pick<CartLine, 'limited'>) {
  return Math.max(1, Math.min(cartQuantityLimit(line), quantity));
}

function createCartLine(product: Product, sku: ProductSku, quantity = 1): CartLine {
  const baseLine = {
    skuId: sku.id,
    productId: product.id,
    title: product.title,
    productType: product.productType,
    coverUrl: product.coverUrl,
    limited: product.limited,
    skuName: sku.skuName,
    originalPriceCents: sku.priceCents,
    vipPriceCents: hasVipDiscount(sku.priceCents, sku.vipPriceCents) ? sku.vipPriceCents : sku.priceCents,
    quantity
  };
  return {
    ...baseLine,
    quantity: normalizeQuantity(quantity, baseLine)
  };
}

function cartUnitPriceCents(line: Pick<CartLine, 'originalPriceCents' | 'vipPriceCents'>, isVip: boolean) {
  return isVip && hasVipDiscount(line.originalPriceCents, line.vipPriceCents)
    ? line.vipPriceCents
    : line.originalPriceCents;
}

function cartLineDiscountCents(line: Pick<CartLine, 'originalPriceCents' | 'vipPriceCents'>, isVip: boolean) {
  return Math.max(0, line.originalPriceCents - cartUnitPriceCents(line, isVip));
}

function normalizeStoredCartLine(line: Partial<CartLine> & { unitPriceCents?: number }) {
  const legacyPrice = typeof line.unitPriceCents === 'number' ? line.unitPriceCents : 0;
  const originalPriceCents = typeof line.originalPriceCents === 'number' ? line.originalPriceCents : legacyPrice;
  const vipPriceCents = typeof line.vipPriceCents === 'number' ? line.vipPriceCents : originalPriceCents;
  return {
    ...line,
    limited: Boolean(line.limited),
    originalPriceCents,
    vipPriceCents,
    quantity: normalizeQuantity(line.quantity ?? 1, { limited: Boolean(line.limited) })
  } as CartLine;
}

function loadStoredCart(storageKey: string): CartLine[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(storageKey)
      ?? (storageKey === cartStorageKey(null) ? window.localStorage.getItem(CART_STORAGE_KEY) : null);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as (Partial<CartLine> & { unitPriceCents?: number })[];
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((line) => (
        typeof line.skuId === 'number' &&
        typeof line.productId === 'number' &&
        typeof line.title === 'string' &&
        typeof line.productType === 'string' &&
        typeof line.coverUrl === 'string' &&
        typeof line.skuName === 'string' &&
        (typeof line.originalPriceCents === 'number' || typeof line.unitPriceCents === 'number') &&
        typeof line.quantity === 'number'
      ))
      .map(normalizeStoredCartLine);
  } catch {
    return [];
  }
}

function errorMessage(error: unknown) {
  if (error instanceof Error && error.message) {
    if (isPurchaseLimitError(error)) return PURCHASE_LIMIT_MESSAGE;
    return error.message.length > 140 ? '操作失败，请稍后重试。' : error.message;
  }
  return '操作失败，请稍后重试。';
}

function rawErrorText(error: unknown) {
  return error instanceof Error ? error.message : '';
}

function isInsufficientPointsError(error: unknown) {
  return rawErrorText(error).toLowerCase().includes('not enough points')
    || rawErrorText(error).includes('POINTS_NOT_ENOUGH');
}

function isPurchaseLimitError(error: unknown) {
  const text = rawErrorText(error).toLowerCase();
  return text.includes('purchase_limit_exceeded')
    || text.includes('限购商品')
    || text.includes('超出购买数量')
    || text.includes('limited item')
    || text.includes('allows only')
    || text.includes('limit per user');
}

function cartPurchaseLimitItemMessages(items: CartLine[]) {
  const overflowItems = items.filter((item) => item.limited && item.quantity > cartQuantityLimit(item));
  const affectedItems = overflowItems.length
    ? overflowItems
    : items.filter((item) => item.limited);

  return affectedItems.reduce<Record<number, string>>((messages, item) => {
    messages[item.skuId] = '该限购商品已超出购买数量';
    return messages;
  }, {});
}

function profileWithRoles(data: AuthResponse): Profile {
  return {
    ...data.profile,
    roles: Array.from(data.roles ?? data.profile.roles ?? [])
  };
}

function clearStoredAuth() {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(AUTH_STORAGE_KEY);
}

function isSessionRejected(error: unknown) {
  return error instanceof ApiRequestError && (
    error.status === 401 ||
    ['SESSION_REQUIRED', 'SESSION_EXPIRED', 'USER_DISABLED'].includes(error.code)
  );
}

function authTitle(mode: AuthMode) {
  if (mode === 'register') return '注册';
  if (mode === 'resetRequest') return '找回密码';
  if (mode === 'resetConfirm') return '重置密码';
  return '登录';
}

function parseRoute(path = window.location.pathname): AppRoute {
  if (path === '/account/bookshelf') return { kind: 'accountBookshelf' };
  if (path === '/account/orders') return { kind: 'accountOrders' };
  if (path === '/account') return { kind: 'account' };
  const readerMatch = path.match(/^\/works\/(\d+)\/chapters\/(\d+)\/read$/);
  if (readerMatch) return { kind: 'reader', workId: Number(readerMatch[1]), chapterId: Number(readerMatch[2]) };
  const workMatch = path.match(/^\/works\/(\d+)$/);
  if (workMatch) return { kind: 'work', id: Number(workMatch[1]) };
  const productMatch = path.match(/^\/products\/(\d+)$/);
  if (productMatch) return { kind: 'product', id: Number(productMatch[1]) };
  return { kind: 'list' };
}

function fallbackWorkPage(section: SectionKey, keyword: string, page: number): PageResult<Work> {
  const normalized = keyword.trim().toLowerCase();
  const items = fallbackWorks.filter((work) => (
    work.workType === section &&
    (!normalized || [work.title, work.author, work.category].some((value) => value.toLowerCase().includes(normalized)))
  ));
  return paginate(items, page);
}

function fallbackProductPage(keyword: string, page: number): PageResult<Product> {
  const normalized = keyword.trim().toLowerCase();
  const items = fallbackProducts.filter((product) => (
    !normalized || [product.title, product.description, product.productType].some((value) => value.toLowerCase().includes(normalized))
  ));
  return paginate(items, page);
}

function paginate<T>(items: T[], page: number): PageResult<T> {
  const start = (page - 1) * PAGE_SIZE;
  return { items: items.slice(start, start + PAGE_SIZE), page, size: PAGE_SIZE, total: items.length };
}

export function App() {
  const [route, setRoute] = useState<AppRoute>(() => parseRoute());
  const [activeSection, setActiveSection] = useState<SectionKey>('NOVEL');
  const [searchDraft, setSearchDraft] = useState('');
  const [submittedKeyword, setSubmittedKeyword] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [workPage, setWorkPage] = useState<PageResult<Work>>(() => fallbackWorkPage('NOVEL', '', 1));
  const [productPage, setProductPage] = useState<PageResult<Product>>(() => fallbackProductPage('', 1));
  const [listLoading, setListLoading] = useState(false);
  const [detailWork, setDetailWork] = useState<Work | null>(null);
  const [detailProduct, setDetailProduct] = useState<Product | null>(null);
  const [chapters, setChapters] = useState<Chapter[]>(fallbackChapters);
  const [readerData, setReaderData] = useState<ReaderResponse | null>(null);
  const [readerLoading, setReaderLoading] = useState(false);
  const [readerError, setReaderError] = useState('');
  const [readerSettings, setReaderSettings] = useState<ReaderSettings>(() => loadStoredReaderSettings());
  const [accountOrders, setAccountOrders] = useState<OrderView[]>([]);
  const [accountLedger, setAccountLedger] = useState<PointsLedgerView[]>([]);
  const [accountBookshelf, setAccountBookshelf] = useState<BookshelfItem[]>([]);
  const [accountReadingProgress, setAccountReadingProgress] = useState<ReadingProgress[]>([]);
  const [accountLoading, setAccountLoading] = useState(false);
  const [accountError, setAccountError] = useState('');
  const [accountPayingOrderNo, setAccountPayingOrderNo] = useState('');
  const [accountCancellingOrderNo, setAccountCancellingOrderNo] = useState('');
  const [bookshelfLoadingWorkId, setBookshelfLoadingWorkId] = useState<number | null>(null);
  const [accountOrderFilter, setAccountOrderFilter] = useState<AccountOrderFilter>('ALL');
  const [token, setToken] = useState('');
  const [profile, setProfile] = useState<Profile | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [, setNotice] = useState('');
  const [cartItems, setCartItems] = useState<CartLine[]>(() => loadStoredCart(cartStorageKey(null)));
  const [selectedCartSkuIds, setSelectedCartSkuIds] = useState<Set<number>>(() => cartSkuIdSet(loadStoredCart(cartStorageKey(null))));
  const [cartOpen, setCartOpen] = useState(false);
  const [cartMessage, setCartMessage] = useState('');
  const [cartItemMessages, setCartItemMessages] = useState<Record<number, string>>({});
  const [cartToast, setCartToast] = useState('');
  const [addingSkuId, setAddingSkuId] = useState<number | null>(null);
  const [checkoutLoading, setCheckoutLoading] = useState(false);
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [pendingPayment, setPendingPayment] = useState<PendingPayment | null>(null);
  const [insufficientPointsOpen, setInsufficientPointsOpen] = useState(false);
  const [rechargeOpen, setRechargeOpen] = useState(false);
  const [rechargeLoadingKey, setRechargeLoadingKey] = useState('');
  const [rechargePaymentLoading, setRechargePaymentLoading] = useState(false);
  const [rechargeMessage, setRechargeMessage] = useState('');
  const [pendingRechargePayment, setPendingRechargePayment] = useState<PendingRechargePayment | null>(null);
  const [accountOpen, setAccountOpen] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [authEmail, setAuthEmail] = useState('');
  const [authUsername, setAuthUsername] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [authResetToken, setAuthResetToken] = useState('');
  const [authMessage, setAuthMessage] = useState('');
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState('');
  const accountCloseTimer = useRef<number | null>(null);
  const cartStorageKeyRef = useRef(cartStorageKey(null));
  const cartToastTimer = useRef<number | null>(null);
  const refreshPromise = useRef<Promise<boolean> | null>(null);

  const isVip = useMemo(() => profile?.roles.includes('VIP') ?? false, [profile]);
  const activeMeta = sections.find((section) => section.key === activeSection) ?? sections[0];
  const activePage = activeSection === 'MEMBER' ? productPage : workPage;
  const totalPages = Math.max(1, Math.ceil((activePage.total || activePage.items.length) / PAGE_SIZE));
  const cartItemCount = cartQuantityTotal(cartItems);
  const selectedCartItems = useMemo(
    () => cartItems.filter((item) => selectedCartSkuIds.has(item.skuId)),
    [cartItems, selectedCartSkuIds]
  );
  const selectedCartItemCount = cartQuantityTotal(selectedCartItems);
  const selectedCartOriginalTotalCents = selectedCartItems.reduce((total, item) => total + item.originalPriceCents * item.quantity, 0);
  const selectedCartTotalCents = selectedCartItems.reduce((total, item) => total + cartUnitPriceCents(item, isVip) * item.quantity, 0);
  const selectedCartDiscountCents = Math.max(0, selectedCartOriginalTotalCents - selectedCartTotalCents);
  const bookshelfWorkIds = useMemo(() => new Set(accountBookshelf.map((item) => item.workId)), [accountBookshelf]);

  const clearAuthState = useCallback(() => {
    clearStoredAuth();
    setToken('');
    setProfile(null);
  }, []);

  const applyAuthResponse = useCallback((data: AuthResponse) => {
    const nextProfile = profileWithRoles(data);
    setToken(data.accessToken);
    setProfile(nextProfile);
    return nextProfile;
  }, []);

  const refreshSession = useCallback(() => {
    if (refreshPromise.current) return refreshPromise.current;
    const request = api<AuthResponse>('/api/v1/auth/refresh', { method: 'POST' })
      .then((data) => {
        applyAuthResponse(data);
        return true;
      })
      .catch((error) => {
        if (isSessionRejected(error)) {
          clearAuthState();
        }
        return false;
      })
      .finally(() => {
        refreshPromise.current = null;
      });
    refreshPromise.current = request;
    return request;
  }, [applyAuthResponse, clearAuthState]);

  useEffect(() => {
    const onPopState = () => setRoute(parseRoute());
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  useEffect(() => {
    let active = true;
    clearStoredAuth();
    void refreshSession().finally(() => {
      if (active) setAuthReady(true);
    });
    return () => {
      active = false;
    };
  }, [refreshSession]);

  useEffect(() => {
    if (!token || !authReady) return;

    const keepAlive = () => {
      void refreshSession();
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') keepAlive();
    };
    const timer = window.setInterval(keepAlive, SESSION_KEEPALIVE_INTERVAL_MS);
    document.addEventListener('visibilitychange', onVisibilityChange);
    window.addEventListener('focus', keepAlive);
    window.addEventListener('online', keepAlive);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      window.removeEventListener('focus', keepAlive);
      window.removeEventListener('online', keepAlive);
    };
  }, [authReady, refreshSession, token]);

  useEffect(() => () => {
    if (accountCloseTimer.current) {
      window.clearTimeout(accountCloseTimer.current);
    }
    if (cartToastTimer.current) {
      window.clearTimeout(cartToastTimer.current);
    }
  }, []);

  useEffect(() => {
    if (!token) return;
    let active = true;

    api<Profile>('/api/v1/users/me', {}, token)
      .then((nextProfile) => {
        if (!active) return;
        setProfile(nextProfile);
      })
      .catch(() => {
        if (!active) return;
        void refreshSession();
      });

    return () => {
      active = false;
    };
  }, [refreshSession, token]);

  useEffect(() => {
    if (!token) {
      setAccountBookshelf([]);
      setAccountReadingProgress([]);
      return;
    }
    let active = true;

    Promise.all([
      api<PageResult<BookshelfItem>>('/api/v1/reading/bookshelf?page=1&size=50', {}, token),
      api<PageResult<ReadingProgress>>('/api/v1/reading/progress?page=1&size=20', {}, token)
    ])
      .then(([bookshelf, progress]) => {
        if (!active) return;
        setAccountBookshelf(bookshelf.items);
        setAccountReadingProgress(progress.items);
      })
      .catch(() => {
        if (!active) return;
        setAccountBookshelf([]);
        setAccountReadingProgress([]);
      });

    return () => {
      active = false;
    };
  }, [token]);

  useEffect(() => {
    const nextKey = cartStorageKey(profile);
    const nextCartItems = loadStoredCart(nextKey);
    cartStorageKeyRef.current = nextKey;
    setCartItems(nextCartItems);
    setSelectedCartSkuIds(cartSkuIdSet(nextCartItems));
    setCartMessage('');
    setCartItemMessages({});
    setPendingPayment(null);
  }, [profile?.id]);

  useEffect(() => {
    window.localStorage.setItem(cartStorageKeyRef.current, JSON.stringify(cartItems));
  }, [cartItems]);

  useEffect(() => {
    window.localStorage.setItem(READER_SETTINGS_STORAGE_KEY, JSON.stringify(readerSettings));
  }, [readerSettings]);

  useEffect(() => {
    if (route.kind !== 'list') return;

    const params = new URLSearchParams({
      page: String(currentPage),
      size: String(PAGE_SIZE)
    });
    if (submittedKeyword) params.set('keyword', submittedKeyword);
    setListLoading(true);

    if (activeSection === 'MEMBER') {
      api<PageResult<Product>>(`/api/v1/products?${params.toString()}`, {}, token)
        .then(setProductPage)
        .catch(() => setProductPage(fallbackProductPage(submittedKeyword, currentPage)))
        .finally(() => setListLoading(false));
      return;
    }

    params.set('type', activeSection);
    api<PageResult<Work>>(`/api/v1/works?${params.toString()}`)
      .then(setWorkPage)
      .catch(() => setWorkPage(fallbackWorkPage(activeSection, submittedKeyword, currentPage)))
      .finally(() => setListLoading(false));
  }, [activeSection, currentPage, route.kind, submittedKeyword, token]);

  useEffect(() => {
    if (route.kind === 'work' || route.kind === 'reader') {
      const workId = route.kind === 'work' ? route.id : route.workId;
      setDetailProduct(null);
      api<WorkDetail>(`/api/v1/works/${workId}`, {}, token)
        .then((data) => {
          setDetailWork(data.work);
          setChapters(data.chapters);
        })
        .catch(() => {
          setDetailWork(fallbackWorks.find((work) => work.id === workId) ?? fallbackWorks[0]);
          setChapters(fallbackChapters);
        });
    }

    if (route.kind === 'reader') {
      setReaderData(null);
      setReaderError('');
      setReaderLoading(true);
      const knownChapter = chapters.find((chapter) => chapter.id === route.chapterId)
        ?? fallbackChapters.find((chapter) => chapter.id === route.chapterId);
      if (!token && knownChapter && !knownChapter.free) {
        setReaderError('章节暂未解锁。');
        setReaderLoading(false);
        return;
      }
      api<ReaderResponse>(`/api/v1/chapters/${route.chapterId}/reader`, {}, token)
        .then(setReaderData)
        .catch((error) => {
          const fallbackChapter = fallbackChapters.find((chapter) => chapter.id === route.chapterId);
          if (fallbackChapter?.free) {
            setReaderData({
              chapterId: fallbackChapter.id,
              title: fallbackChapter.title,
              unlocked: true,
              text: fallbackReaderText,
              images: [],
              progressPercent: 0
            });
            return;
          }
          setReaderError(errorMessage(error));
        })
        .finally(() => setReaderLoading(false));
    }

    if (route.kind === 'product') {
      setDetailWork(null);
      setReaderData(null);
      setReaderError('');
      api<Product>(`/api/v1/products/${route.id}`, {}, token)
        .then(setDetailProduct)
        .catch(() => setDetailProduct(fallbackProducts.find((product) => product.id === route.id) ?? fallbackProducts[0]));
    }
  }, [route, token]);

  useEffect(() => {
    const isAccountRoute = route.kind === 'account' || route.kind === 'accountBookshelf' || route.kind === 'accountOrders';
    if (!isAccountRoute || !authReady) return;
    setDetailWork(null);
    setDetailProduct(null);
    setReaderData(null);
    setReaderError('');
    if (!token) {
      setAccountOrders([]);
      setAccountLedger([]);
      setAccountBookshelf([]);
      setAccountReadingProgress([]);
      setAccountError('请先登录后查看个人中心。');
      return;
    }
    void loadAccountData();
  }, [route.kind, token, accountOrderFilter, authReady]);

  function navigate(path: string) {
    window.history.pushState(null, '', path);
    setRoute(parseRoute(path));
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function updateReaderSettings(patch: Partial<ReaderSettings>) {
    setReaderSettings((settings) => sanitizeReaderSettings({ ...settings, ...patch }));
  }

  function showList() {
    window.history.pushState(null, '', '/');
    setRoute({ kind: 'list' });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function switchSection(section: SectionKey) {
    setActiveSection(section);
    setSearchDraft('');
    setSubmittedKeyword('');
    setCurrentPage(1);
    if (route.kind !== 'list') {
      window.history.pushState(null, '', '/');
      setRoute({ kind: 'list' });
    }
  }

  function browseMemberGoods() {
    setCartOpen(false);
    switchSection('MEMBER');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function showCartToast(message: string) {
    setCartToast(message);
    if (cartToastTimer.current) {
      window.clearTimeout(cartToastTimer.current);
    }
    cartToastTimer.current = window.setTimeout(() => {
      setCartToast('');
      cartToastTimer.current = null;
    }, 1000);
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmittedKeyword(searchDraft.trim());
    setCurrentPage(1);
  }

  function openAuth(mode: AuthMode) {
    setAuthMode(mode);
    setAuthError('');
    setAuthMessage('');
    setAuthOpen(true);
    setAccountOpen(false);
  }

  function closeAuth() {
    if (!authLoading) {
      setAuthOpen(false);
      setAuthError('');
      setAuthMessage('');
    }
  }

  function openRecharge() {
    if (!token) {
      openAuth('login');
      return;
    }
    setRechargeMessage('');
    setRechargeOpen(true);
    setAccountOpen(false);
  }

  function closeRecharge() {
    if (!rechargeLoadingKey && !rechargePaymentLoading) {
      const hadPendingPayment = Boolean(pendingRechargePayment);
      setRechargeOpen(false);
      setRechargeMessage('');
      setPendingRechargePayment(null);
      if (hadPendingPayment && route.kind === 'account') {
        void loadAccountData(false);
      }
    }
  }

  function openRechargeFromInsufficientPoints() {
    setInsufficientPointsOpen(false);
    openRecharge();
  }

  function openAccountCenter() {
    setAccountOpen(false);
    if (!token) {
      openAuth('login');
      return;
    }
    navigate('/account');
  }

  async function refreshProfile() {
    if (!token) return;
    const nextProfile = await api<Profile>('/api/v1/users/me', {}, token);
    setProfile(nextProfile);
  }

  async function loadAccountData(showLoading = true, filter = accountOrderFilter) {
    if (!token) return;
    if (showLoading) setAccountLoading(true);
    setAccountError('');
    const orderParams = new URLSearchParams({ page: '1', size: '20' });
    if (filter !== 'ALL') {
      orderParams.set('status', filter);
    }
    try {
      const [orders, ledger, bookshelf, progress] = await Promise.all([
        api<PageResult<OrderView>>(`/api/v1/orders?${orderParams.toString()}`, {}, token),
        api<PageResult<PointsLedgerView>>('/api/v1/users/me/points-ledger?page=1&size=20', {}, token),
        api<PageResult<BookshelfItem>>('/api/v1/reading/bookshelf?page=1&size=50', {}, token),
        api<PageResult<ReadingProgress>>('/api/v1/reading/progress?page=1&size=20', {}, token)
      ]);
      setAccountOrders(orders.items);
      setAccountLedger(ledger.items);
      setAccountBookshelf(bookshelf.items);
      setAccountReadingProgress(progress.items);
    } catch (error) {
      setAccountError(errorMessage(error));
    } finally {
      if (showLoading) setAccountLoading(false);
    }
  }

  async function saveReadingProgress(chapterId: number, progressPercent: number) {
    if (!token) return;
    try {
      const progress = await api<ReadingProgress>('/api/v1/reading/progress', {
        method: 'PUT',
        body: JSON.stringify({
          chapterId,
          progressPercent: normalizeProgressPercent(progressPercent)
        })
      }, token);
      setAccountReadingProgress((items) => [progress, ...items.filter((item) => item.workId !== progress.workId)]);
      setAccountBookshelf((items) => items.map((item) => (
        item.workId === progress.workId
          ? {
              ...item,
              lastChapterId: progress.chapterId,
              lastChapterNo: progress.chapterNo,
              lastChapterTitle: progress.chapterTitle,
              progressPercent: progress.progressPercent,
              updatedAt: progress.updatedAt
            }
          : item
      )));
    } catch {
      // Reading progress is a quiet background save; avoid interrupting reading.
    }
  }

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAuthLoading(true);
    setAuthError('');
    setAuthMessage('');
    try {
      if (authMode === 'resetRequest') {
        const data = await api<PasswordResetResponse>('/api/v1/auth/password-reset/request', {
          method: 'POST',
          body: JSON.stringify({ email: authEmail.trim() })
        });
        setAuthResetToken(data.devToken ?? '');
        setAuthPassword('');
        setAuthMode('resetConfirm');
        setAuthMessage('验证码已发送，请设置新密码。');
        return;
      }
      if (authMode === 'resetConfirm') {
        await api('/api/v1/auth/password-reset/confirm', {
          method: 'POST',
          body: JSON.stringify({ token: authResetToken.trim(), newPassword: authPassword })
        });
        clearAuthState();
        setAuthMode('login');
        setAuthPassword('');
        setAuthResetToken('');
        setAuthMessage('密码已重置，请重新登录。');
        return;
      }
      const isRegister = authMode === 'register';
      const data = await api<AuthResponse>(isRegister ? '/api/v1/auth/register' : '/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({
          email: authEmail.trim(),
          password: authPassword,
          ...(isRegister ? { username: authUsername.trim() } : {})
        })
      });
      applyAuthResponse(data);
      setAuthOpen(false);
      setAuthPassword('');
      setAuthError('');
      setNotice(isRegister ? '注册成功，已登录。' : '登录成功。');
    } catch (error) {
      setAuthError(errorMessage(error));
    } finally {
      setAuthLoading(false);
    }
  }

  async function checkin() {
    if (!token) {
      setNotice('请先登录。');
      openAuth('login');
      return;
    }
    try {
      const data = await api<{ gained: number; totalPoints: number; alreadyCheckedIn: boolean }>('/api/v1/checkins', { method: 'POST' }, token);
      setProfile((old) => (old ? { ...old, points: data.totalPoints } : old));
      setNotice(data.alreadyCheckedIn ? '今天已经签到过。' : `签到成功，获得 ${data.gained} 积分。`);
      setAccountOpen(false);
    } catch (error) {
      setNotice(errorMessage(error));
    }
  }

  function openChapter(chapter: Chapter) {
    const workId = route.kind === 'work' ? route.id : route.kind === 'reader' ? route.workId : detailWork?.id;
    if (!workId) return;
    navigate(`/works/${workId}/chapters/${chapter.id}/read`);
    setNotice(`${chapter.title} 已打开。`);
  }

  async function purchaseChapter(chapter: Chapter) {
    if (!token) {
      setNotice(`请先登录。登录后可用 ${chapter.pricePoints} 积分兑换该章节，购买后永久可读；VIP 可免费阅读。`);
      openAuth('login');
      return;
    }
    try {
      await api(`/api/v1/chapters/${chapter.id}/purchase`, { method: 'POST' }, token);
      setChapters((items) => items.map((item) => (
        item.id === chapter.id ? { ...item, unlocked: true, accessLabel: '已解锁' } : item
      )));
      setNotice(`兑换成功，消耗 ${chapter.pricePoints} 积分；该章节已永久解锁。`);
      openChapter(chapter);
    } catch (error) {
      if (isInsufficientPointsError(error)) {
        setInsufficientPointsOpen(true);
        return;
      }
      setNotice(`兑换失败：该章节需要 ${chapter.pricePoints} 积分。VIP 可免费阅读，普通用户兑换后永久可读。`);
    }
  }

  async function purchaseRechargeOption(option: RechargeOption) {
    if (!token) {
      setNotice('请先登录。');
      openAuth('login');
      return;
    }
    setRechargeLoadingKey(option.id);
    setRechargeMessage('');
    setPendingRechargePayment(null);
    try {
      const order = option.type === 'VIP'
        ? await api<OrderView>('/api/v1/vip/orders', { method: 'POST' }, token)
        : await api<OrderView>('/api/v1/orders/points', {
            method: 'POST',
            body: JSON.stringify({ amountCents: option.amountCents })
          }, token);
      if (!order.paymentNo) {
        throw new Error('订单未生成支付号。');
      }
      setPendingRechargePayment({
        orderNo: order.orderNo,
        paymentNo: order.paymentNo,
        amountCents: order.totalCents ?? option.amountCents,
        status: order.status,
        optionTitle: option.title,
        successMessage: option.type === 'VIP' ? 'VIP 已开通。' : `已充值 ${option.points} 积分。`
      });
      setRechargeMessage('请完成支付。');
    } catch (error) {
      setRechargeMessage(errorMessage(error));
    } finally {
      setRechargeLoadingKey('');
    }
  }

  async function confirmRechargePayment() {
    if (!pendingRechargePayment || rechargePaymentLoading) return;
    if (!token) {
      setRechargeOpen(false);
      openAuth('login');
      return;
    }

    setRechargePaymentLoading(true);
    setRechargeMessage('');
    try {
      const confirmed = await executePayment(pendingRechargePayment, token);
      if (!confirmed) return;
      await refreshProfile();
      setRechargeMessage(pendingRechargePayment.successMessage);
      setPendingRechargePayment(null);
    } catch (error) {
      setRechargeMessage(errorMessage(error));
    } finally {
      setRechargePaymentLoading(false);
    }
  }

  async function payAccountOrder(order: OrderView) {
    if (!token) {
      openAuth('login');
      return;
    }
    if (!order.paymentNo || accountPayingOrderNo) return;
    setAccountPayingOrderNo(order.orderNo);
    setAccountError('');
    try {
      const confirmed = await executePayment({
        orderNo: order.orderNo,
        paymentNo: order.paymentNo,
        amountCents: order.totalCents
      }, token);
      if (!confirmed) return;
      await refreshProfile();
      await loadAccountData(false);
    } catch (error) {
      setAccountError(errorMessage(error));
    } finally {
      setAccountPayingOrderNo('');
    }
  }

  async function cancelAccountOrder(order: OrderView) {
    if (!token) {
      openAuth('login');
      return;
    }
    if (!canCancelOrder(order) || accountCancellingOrderNo) return;
    setAccountCancellingOrderNo(order.orderNo);
    setAccountError('');
    try {
      await api<OrderView>(`/api/v1/orders/${order.orderNo}/cancel`, { method: 'POST' }, token);
      await loadAccountData(false);
    } catch (error) {
      setAccountError(errorMessage(error));
    } finally {
      setAccountCancellingOrderNo('');
    }
  }

  async function toggleBookshelf(work: Work) {
    if (!token) {
      openAuth('login');
      return;
    }
    if (bookshelfLoadingWorkId) return;
    const inBookshelf = bookshelfWorkIds.has(work.id);
    setBookshelfLoadingWorkId(work.id);
    try {
      if (inBookshelf) {
        await api(`/api/v1/works/${work.id}/bookshelf`, { method: 'DELETE' }, token);
        setAccountBookshelf((items) => items.filter((item) => item.workId !== work.id));
      } else {
        const item = await api<BookshelfItem>(`/api/v1/works/${work.id}/bookshelf`, { method: 'POST' }, token);
        setAccountBookshelf((items) => [item, ...items.filter((old) => old.workId !== item.workId)]);
      }
    } catch (error) {
      setAccountError(errorMessage(error));
    } finally {
      setBookshelfLoadingWorkId(null);
    }
  }

  async function addProductToCart(product: Product, sku: ProductSku | undefined = product.skus[0]) {
    if (!sku) {
      setCartMessage('该商品暂无可购买规格。');
      showCartToast('该商品暂无可购买规格');
      return;
    }

    const line = createCartLine(product, sku);
    const alreadyAtLimit = product.limited && cartItems.some((item) => (
      item.skuId === line.skuId && item.quantity >= cartQuantityLimit(item)
    ));
    setPendingPayment(null);
    setCartMessage('');
    setCartItemMessages((messages) => {
      const next = { ...messages };
      delete next[line.skuId];
      return next;
    });
    setSelectedCartSkuIds((selected) => new Set(selected).add(line.skuId));
    setAddingSkuId(sku.id);
    setCartItems((items) => {
      const existing = items.find((item) => item.skuId === line.skuId);
      if (!existing) return [line, ...items];
      return items.map((item) => (
        item.skuId === line.skuId
          ? {
              ...item,
              ...line,
              quantity: normalizeQuantity(item.quantity + line.quantity, line)
            }
          : item
      ));
    });
    showCartToast(alreadyAtLimit ? `限购商品「${product.title}」最多 1 件` : '已加入购物车');

    if (!token) {
      setAddingSkuId(null);
      return;
    }

    try {
      await api('/api/v1/cart/items', {
        method: 'POST',
        body: JSON.stringify({ skuId: sku.id, quantity: line.quantity })
      }, token);
    } catch (error) {
      if (isPurchaseLimitError(error)) {
        setCartMessage('');
        setCartItemMessages(cartPurchaseLimitItemMessages([line]));
      } else {
        setCartMessage(`已保存在本地购物车；${errorMessage(error)}`);
      }
    } finally {
      setAddingSkuId(null);
    }
  }

  async function checkoutCart() {
    if (!cartItems.length || !selectedCartItems.length || checkoutLoading) return;
    if (selectedCartItems.some((item) => item.limited && item.quantity > cartQuantityLimit(item))) {
      setCartMessage('');
      setCartItemMessages(cartPurchaseLimitItemMessages(selectedCartItems));
      return;
    }
    if (!token) {
      setCartOpen(false);
      openAuth('login');
      return;
    }

    setCheckoutLoading(true);
    setCartMessage('');
    setCartItemMessages({});
    try {
      const order = await api<OrderView>('/api/v1/orders', {
        method: 'POST',
        body: JSON.stringify({
          items: selectedCartItems.map((item) => ({ skuId: item.skuId, quantity: item.quantity }))
        })
      }, token);
      if (!order.paymentNo) {
        throw new Error('订单未生成支付号。');
      }
      setPendingPayment({
        orderNo: order.orderNo,
        paymentNo: order.paymentNo,
        amountCents: order.totalCents ?? selectedCartTotalCents,
        status: order.status,
        itemSkuIds: selectedCartItems.map((item) => item.skuId)
      });
      setCartMessage('订单已创建，请确认支付。');
    } catch (error) {
      const message = errorMessage(error);
      if (
        isPurchaseLimitError(error)
          || message === PURCHASE_LIMIT_MESSAGE
          || (selectedCartItems.some((item) => item.limited) && message === '操作失败，请稍后重试。')
      ) {
        setCartMessage('');
        setCartItemMessages(cartPurchaseLimitItemMessages(selectedCartItems));
      } else {
        setCartMessage(message);
      }
    } finally {
      setCheckoutLoading(false);
    }
  }

  async function confirmCartPayment() {
    if (!pendingPayment || paymentLoading) return;
    if (!token) {
      setCartOpen(false);
      openAuth('login');
      return;
    }

    setPaymentLoading(true);
    setCartMessage('');
    setCartItemMessages({});
    try {
      const confirmed = await executePayment(pendingPayment, token);
      if (!confirmed) return;
      const paidSkuIds = new Set(pendingPayment.itemSkuIds ?? cartItems.map((item) => item.skuId));
      const remainingItems = cartItems.filter((item) => !paidSkuIds.has(item.skuId));
      setCartItems(remainingItems);
      setSelectedCartSkuIds((selected) => {
        const next = new Set<number>();
        for (const skuId of selected) {
          if (!paidSkuIds.has(skuId) && remainingItems.some((item) => item.skuId === skuId)) {
            next.add(skuId);
          }
        }
        return next;
      });
      setPendingPayment(null);
      setCartMessage('');
      setCartItemMessages({});
      setCartOpen(remainingItems.length > 0);
      showCartToast('支付成功');
    } catch (error) {
      setCartMessage(errorMessage(error));
    } finally {
      setPaymentLoading(false);
    }
  }

  function changeCartQuantity(skuId: number, quantity: number) {
    setPendingPayment(null);
    setCartMessage('');
    setCartItemMessages((messages) => {
      const next = { ...messages };
      delete next[skuId];
      return next;
    });
    if (quantity <= 0) {
      setSelectedCartSkuIds((selected) => {
        const next = new Set(selected);
        next.delete(skuId);
        return next;
      });
    }
    setCartItems((items) => items.flatMap((item) => {
      if (item.skuId !== skuId) return [item];
      if (quantity <= 0) return [];
      return [{ ...item, quantity: normalizeQuantity(quantity, item) }];
    }));
  }

  function toggleCartItemSelected(skuId: number) {
    setPendingPayment(null);
    setCartMessage('');
    setCartItemMessages((messages) => {
      const next = { ...messages };
      delete next[skuId];
      return next;
    });
    setSelectedCartSkuIds((selected) => {
      const next = new Set(selected);
      if (next.has(skuId)) {
        next.delete(skuId);
      } else {
        next.add(skuId);
      }
      return next;
    });
  }

  function toggleAllCartItemsSelected() {
    setPendingPayment(null);
    setCartMessage('');
    setCartItemMessages({});
    setSelectedCartSkuIds((selected) => (
      cartItems.length > 0 && cartItems.every((item) => selected.has(item.skuId))
        ? new Set()
        : cartSkuIdSet(cartItems)
    ));
  }

  function removeCartItem(skuId: number) {
    setPendingPayment(null);
    setCartMessage('');
    setCartItemMessages((messages) => {
      const next = { ...messages };
      delete next[skuId];
      return next;
    });
    setSelectedCartSkuIds((selected) => {
      const next = new Set(selected);
      next.delete(skuId);
      return next;
    });
    setCartItems((items) => items.filter((item) => item.skuId !== skuId));
  }

  function removeSelectedCartItems() {
    if (!selectedCartSkuIds.size) return;
    setPendingPayment(null);
    setCartMessage('');
    setCartItemMessages((messages) => {
      const next = { ...messages };
      for (const skuId of selectedCartSkuIds) {
        delete next[skuId];
      }
      return next;
    });
    setCartItems((items) => items.filter((item) => !selectedCartSkuIds.has(item.skuId)));
    setSelectedCartSkuIds(new Set());
  }

  async function logout() {
    try {
      await api('/api/v1/auth/logout', { method: 'POST' });
    } catch {
      // Local logout still completes when the server is temporarily unavailable.
    }
    clearAuthState();
    setPendingPayment(null);
    setAccountBookshelf([]);
    setAccountReadingProgress([]);
    setAccountOpen(false);
    setNotice('已退出登录。');
  }

  function openAccountMenu() {
    if (accountCloseTimer.current) {
      window.clearTimeout(accountCloseTimer.current);
      accountCloseTimer.current = null;
    }
    setAccountOpen(true);
  }

  function closeAccountMenuSoon() {
    if (accountCloseTimer.current) {
      window.clearTimeout(accountCloseTimer.current);
    }
    accountCloseTimer.current = window.setTimeout(() => {
      setAccountOpen(false);
      accountCloseTimer.current = null;
    }, 220);
  }

  return (
    <main className="shell">
      <header className="topbar">
        <button className="brandButton" type="button" onClick={showList}><Sparkles size={18} /> Gray Shelf</button>
        <nav>
          {sections.map((section) => (
            <button className={activeSection === section.key ? 'navActive' : ''} key={section.key} type="button" onClick={() => switchSection(section.key)}>
              {section.label}
            </button>
          ))}
        </nav>
        <div className="headerActions">
          <button
            className={`cartTrigger ${cartItemCount ? 'hasItems' : ''}`}
            type="button"
            aria-label={`打开购物车，当前 ${cartItemCount} 件商品`}
            onClick={() => setCartOpen(true)}
          >
            <ShoppingBag size={18} />
            <span>购物车</span>
            {cartItemCount > 0 && <b>{cartItemCount}</b>}
          </button>
          <div
            className={`accountMenu ${accountOpen ? 'isOpen' : ''}`}
            onMouseEnter={openAccountMenu}
            onMouseLeave={closeAccountMenuSoon}
          >
            <button
              className="accountTrigger"
              type="button"
              aria-haspopup="menu"
              aria-expanded={accountOpen}
              onClick={() => setAccountOpen((open) => !open)}
              onFocus={openAccountMenu}
            >
              <span className="avatar">
                <UserRound size={18} />
              </span>
              <span>{profile ? profile.username : '登录'}</span>
              <ChevronDown size={15} />
            </button>

            <div className="accountDropdown" role="menu">
              {profile ? (
                <>
                  <div className="accountSummary">
                    <b>{profile.username}</b>
                    <span>{profile.email}</span>
                    <small>{profile.points} 积分 · {isVip ? 'VIP 用户' : '普通用户'}</small>
                  </div>
                  <button type="button" role="menuitem" className="dropdownAction" onClick={openAccountCenter}>
                    <UserRound size={16} /> 个人中心
                  </button>
                  <button type="button" role="menuitem" className="dropdownAction" onClick={openRecharge}>
                    <Coins size={16} /> 积分充值 / VIP
                  </button>
                  <button type="button" role="menuitem" className="dropdownAction" onClick={checkin}>
                    <Gift size={16} /> 每日签到
                  </button>
                  <button type="button" role="menuitem" className="dropdownAction muted" onClick={logout}>
                    <LogOut size={16} /> 退出登录
                  </button>
                </>
              ) : (
                <>
                  <button type="button" role="menuitem" className="dropdownAction" onClick={() => openAuth('login')}>
                    <LogIn size={16} /> 登录
                  </button>
                  <button type="button" role="menuitem" className="dropdownAction" onClick={() => openAuth('register')}>
                    <UserPlus size={16} /> 注册
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      </header>

      {route.kind === 'list' ? (
        <>
          <section className="hero compactHero">
            <div className="heroCopy">
              <p className="eyebrow">NOVEL · MANGA · MEMBER GOODS</p>
              <h1>Gray Shelf</h1>
            </div>
          </section>

          <section className="catalogShell" id="catalog">
            <form className="sectionSearch" onSubmit={submitSearch}>
              <Search size={18} />
              <input
                value={searchDraft}
                onChange={(event) => setSearchDraft(event.target.value)}
                placeholder={activeSection === 'MEMBER' ? '在会员购中搜索商品名或简介' : `在${activeMeta.label}中搜索书名、作者或分类`}
              />
              <button type="submit">搜索</button>
            </form>

            <div className="resultHeader">
              <div>
                <p className="eyebrow">{activeSection === 'MEMBER' ? 'MEMBER GOODS' : activeSection}</p>
                <h2>{submittedKeyword ? `“${submittedKeyword}”的搜索结果` : `${activeMeta.label}最新内容`}</h2>
              </div>
            </div>

            {activeSection === 'MEMBER' ? (
              <ProductResultList
                addingSkuId={addingSkuId}
                products={productPage.items}
                loading={listLoading}
                onAddToCart={addProductToCart}
                onOpen={(product) => navigate(`/products/${product.id}`)}
              />
            ) : (
              <WorkResultList works={workPage.items} loading={listLoading} onOpen={(work) => navigate(`/works/${work.id}`)} />
            )}

            <div className="pager">
              <button type="button" disabled={currentPage <= 1 || listLoading} onClick={() => setCurrentPage((page) => Math.max(1, page - 1))}>上一页</button>
              <span>{currentPage} / {totalPages}</span>
              <button type="button" disabled={currentPage >= totalPages || listLoading} onClick={() => setCurrentPage((page) => page + 1)}>下一页</button>
            </div>
          </section>
        </>
      ) : route.kind === 'work' ? (
        <WorkDetailPage
          bookshelfLoading={bookshelfLoadingWorkId === detailWork?.id}
          chapters={chapters}
          isBookmarked={detailWork ? bookshelfWorkIds.has(detailWork.id) : false}
          work={detailWork}
          onBack={showList}
          onOpenChapter={openChapter}
          onPurchaseChapter={purchaseChapter}
          onToggleBookshelf={toggleBookshelf}
        />
      ) : route.kind === 'reader' ? (
        <ReaderPage
          chapters={chapters}
          currentChapterId={route.chapterId}
          error={readerError}
          loading={readerLoading}
          reader={readerData}
          settings={readerSettings}
          work={detailWork}
          onBack={() => navigate(`/works/${route.workId}`)}
          onOpenChapter={openChapter}
          onPurchaseChapter={purchaseChapter}
          onSaveProgress={saveReadingProgress}
          onSettingsChange={updateReaderSettings}
        />
      ) : route.kind === 'account' || route.kind === 'accountBookshelf' || route.kind === 'accountOrders' ? (
        <AccountCenterPage
          bookshelf={accountBookshelf}
          cancellingOrderNo={accountCancellingOrderNo}
          error={accountError}
          isVip={isVip}
          ledger={accountLedger}
          loading={accountLoading}
          mode={route.kind === 'accountBookshelf' ? 'bookshelf' : route.kind === 'accountOrders' ? 'orders' : 'home'}
          orderFilter={accountOrderFilter}
          orders={accountOrders}
          payingOrderNo={accountPayingOrderNo}
          profile={profile}
          readingProgress={accountReadingProgress}
          onBack={route.kind === 'account' ? showList : () => navigate('/account')}
          onCancelOrder={cancelAccountOrder}
          onContinueReading={(item) => navigate(`/works/${item.workId}/chapters/${item.chapterId}/read`)}
          onFilterChange={setAccountOrderFilter}
          onLogin={() => openAuth('login')}
          onOpenBookshelf={() => navigate('/account/bookshelf')}
          onOpenOrders={() => {
            setAccountOrderFilter('ALL');
            navigate('/account/orders');
          }}
          onOpenWork={(workId) => navigate(`/works/${workId}`)}
          onPayOrder={payAccountOrder}
          onRecharge={openRecharge}
        />
      ) : (
        <ProductDetailPage
          addingSkuId={addingSkuId}
          product={detailProduct}
          onAddToCart={addProductToCart}
          onBack={showList}
        />
      )}

      {cartOpen && (
        <CartPanel
          checkoutLoading={checkoutLoading}
          discountCents={selectedCartDiscountCents}
          isVip={isVip}
          itemMessages={cartItemMessages}
          items={cartItems}
          message={cartMessage}
          originalTotalCents={selectedCartOriginalTotalCents}
          selectedItemCount={selectedCartItemCount}
          selectedSkuIds={selectedCartSkuIds}
          totalCents={selectedCartTotalCents}
          onBrowseMemberGoods={browseMemberGoods}
          onChangeQuantity={changeCartQuantity}
          onCheckout={checkoutCart}
          onConfirmPayment={confirmCartPayment}
          onClose={() => setCartOpen(false)}
          onRemove={removeCartItem}
          onRemoveSelected={removeSelectedCartItems}
          onToggleAll={toggleAllCartItemsSelected}
          onToggleItem={toggleCartItemSelected}
          paymentLoading={paymentLoading}
          pendingPayment={pendingPayment}
        />
      )}

      {cartToast && (
        <div className="cartToast" role="status" aria-live="polite">
          {cartToast}
        </div>
      )}

      {authOpen && (
        <div className="modalLayer" onMouseDown={(event) => event.currentTarget === event.target && closeAuth()}>
          <section className="authDialog" role="dialog" aria-modal="true" aria-labelledby="auth-title">
            <button className="modalClose" type="button" aria-label="关闭账号窗口" onClick={closeAuth}>
              <X size={18} />
            </button>
            <h2 id="auth-title">{authTitle(authMode)}</h2>
            <form className="authForm" onSubmit={submitAuth}>
              {authMode !== 'resetConfirm' && (
                <label className="authField">
                  <span>邮箱</span>
                  <input
                    autoFocus
                    autoComplete="email"
                    required
                    type="email"
                    value={authEmail}
                    onChange={(event) => setAuthEmail(event.target.value)}
                    placeholder="请输入邮箱"
                  />
                </label>
              )}
              {authMode === 'register' && (
                <label className="authField">
                  <span>昵称</span>
                  <input
                    autoComplete="username"
                    required
                    minLength={2}
                    maxLength={30}
                    value={authUsername}
                    onChange={(event) => setAuthUsername(event.target.value)}
                    placeholder="请输入昵称"
                  />
                </label>
              )}
              {authMode === 'resetConfirm' && (
                <label className="authField">
                  <span>验证码</span>
                  <input
                    autoFocus
                    autoComplete="one-time-code"
                    required
                    value={authResetToken}
                    onChange={(event) => setAuthResetToken(event.target.value)}
                    placeholder="请输入验证码"
                  />
                </label>
              )}
              {authMode !== 'resetRequest' && (
                <label className="authField">
                  <span>{authMode === 'resetConfirm' ? '新密码' : '密码'}</span>
                  <input
                    autoComplete={authMode === 'login' ? 'current-password' : 'new-password'}
                    required
                    minLength={6}
                    maxLength={64}
                    type="password"
                    value={authPassword}
                    onChange={(event) => setAuthPassword(event.target.value)}
                    placeholder={authMode === 'resetConfirm' ? '请输入新密码' : '请输入密码'}
                  />
                </label>
              )}
              {authMode === 'login' && (
                <button className="authTextAction" type="button" onClick={() => openAuth('resetRequest')}>
                  忘记密码
                </button>
              )}
              {authMessage && <div className="authMessage" role="status">{authMessage}</div>}
              {authError && <div className="authError" role="alert">{authError}</div>}
              <div className="authActions">
                <button
                  className="secondary"
                  type="button"
                  onClick={() => {
                    setAuthMode(authMode === 'login' ? 'register' : 'login');
                    setAuthError('');
                    setAuthMessage('');
                  }}
                >
                  {authMode === 'login' ? '注册' : '返回登录'}
                </button>
                <button type="submit" disabled={authLoading}>
                  {authMode === 'login' && <LogIn size={17} />}
                  {authMode === 'register' && <UserPlus size={17} />}
                  {(authMode === 'resetRequest' || authMode === 'resetConfirm') && <Lock size={17} />}
                  {authLoading
                    ? '处理中...'
                    : authMode === 'login'
                      ? '登录'
                      : authMode === 'register'
                        ? '注册'
                        : authMode === 'resetRequest'
                          ? '发送验证码'
                          : '重置密码'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}

      {insufficientPointsOpen && (
        <InsufficientPointsDialog
          onClose={() => setInsufficientPointsOpen(false)}
          onRecharge={openRechargeFromInsufficientPoints}
        />
      )}

      {rechargeOpen && (
        <RechargeDialog
          loadingKey={rechargeLoadingKey}
          message={rechargeMessage}
          options={rechargeOptions}
          paymentLoading={rechargePaymentLoading}
          pendingPayment={pendingRechargePayment}
          profile={profile}
          onClose={closeRecharge}
          onConfirmPayment={confirmRechargePayment}
          onSelect={purchaseRechargeOption}
        />
      )}
    </main>
  );
}

function InsufficientPointsDialog({
  onClose,
  onRecharge
}: {
  onClose: () => void;
  onRecharge: () => void;
}) {
  return (
    <div className="modalLayer" onMouseDown={(event) => event.currentTarget === event.target && onClose()}>
      <section className="insufficientDialog" role="dialog" aria-modal="true" aria-labelledby="points-title">
        <button className="modalClose" type="button" aria-label="关闭积分不足提示" onClick={onClose}>
          <X size={18} />
        </button>
        <Coins size={28} />
        <h2 id="points-title">积分不足请充值</h2>
        <button type="button" onClick={onRecharge}>去充值</button>
      </section>
    </div>
  );
}

function RechargeDialog({
  loadingKey,
  message,
  options,
  paymentLoading,
  pendingPayment,
  profile,
  onClose,
  onConfirmPayment,
  onSelect
}: {
  loadingKey: string;
  message: string;
  options: RechargeOption[];
  paymentLoading: boolean;
  pendingPayment: PendingRechargePayment | null;
  profile: Profile | null;
  onClose: () => void;
  onConfirmPayment: () => void;
  onSelect: (option: RechargeOption) => void;
}) {
  return (
    <div className="modalLayer" onMouseDown={(event) => event.currentTarget === event.target && onClose()}>
      <section className="rechargeDialog" role="dialog" aria-modal="true" aria-labelledby="recharge-title">
        <button className="modalClose" type="button" aria-label="关闭充值窗口" onClick={onClose}>
          <X size={18} />
        </button>
        <header>
          <h2 id="recharge-title">充值中心</h2>
          {profile && <small>当前 {profile.points} 积分 · {profile.roles.includes('VIP') ? 'VIP 用户' : '普通用户'}</small>}
        </header>
        <div className="rechargeGrid">
          {options.map((option) => {
            const loading = loadingKey === option.id;
            return (
              <button
                className={`rechargeOption ${option.type === 'VIP' ? 'vip' : ''}`}
                aria-label={`${option.title}，${option.caption}`}
                disabled={Boolean(pendingPayment) || Boolean(loadingKey)}
                key={option.id}
                type="button"
                onClick={() => onSelect(option)}
              >
                {option.type === 'VIP' ? <Crown size={20} /> : <Coins size={20} />}
                <span>{option.title}</span>
                <small>{option.caption}</small>
                {loading && <b>处理中...</b>}
              </button>
            );
          })}
        </div>
        {pendingPayment && (
          <PaymentPrompt
            loading={paymentLoading}
            payment={pendingPayment}
            onConfirm={onConfirmPayment}
          />
        )}
        {message && <p className="rechargeMessage">{message}</p>}
      </section>
    </div>
  );
}

function WorkResultList({ works, loading, onOpen }: { works: Work[]; loading: boolean; onOpen: (work: Work) => void }) {
  if (loading) return <div className="emptyState">正在加载当前分区...</div>;
  if (!works.length) return <div className="emptyState">当前分区没有匹配结果。</div>;

  return (
    <div className="resultList">
      {works.map((work) => (
        <article className="resultCard" key={work.id}>
          <button className="resultCover" type="button" onClick={() => onOpen(work)} aria-label={`打开${work.title}详情`}>
            <img src={work.coverUrl} alt={work.title} />
          </button>
          <button className="resultCopy" type="button" onClick={() => onOpen(work)}>
            <span className="eyebrow">{work.workType} · {work.category}</span>
            <h3>{work.title}</h3>
            <p>{work.description}</p>
            <small>{work.author} · 热度 {work.popularity}</small>
          </button>
        </article>
      ))}
    </div>
  );
}

function ProductResultList({
  addingSkuId,
  products,
  loading,
  onAddToCart,
  onOpen
}: {
  addingSkuId: number | null;
  products: Product[];
  loading: boolean;
  onAddToCart: (product: Product, sku?: ProductSku) => void;
  onOpen: (product: Product) => void;
}) {
  if (loading) return <div className="emptyState">正在加载会员购...</div>;
  if (!products.length) return <div className="emptyState">会员购没有匹配结果。</div>;

  return (
    <div className="resultList">
      {products.map((product) => {
        const sku = product.skus[0];
        const price = sku?.priceCents ?? 0;
        return (
          <article className="resultCard productCard" key={product.id}>
            <button className="resultCover" type="button" onClick={() => onOpen(product)} aria-label={`打开${product.title}详情`}>
              <img src={product.coverUrl} alt={product.title} />
            </button>
            <div className="resultCopy productCopy">
              <span className="eyebrow">{product.productType}{product.limited ? ' · LIMITED' : ''}</span>
              <h3>{product.title}</h3>
              <p>{product.description}</p>
              <small>{money(price)}</small>
              <div className="productCardActions">
                <button type="button" className="secondary" onClick={() => onOpen(product)}>查看详情</button>
                <button type="button" disabled={!sku || addingSkuId === sku.id} onClick={() => onAddToCart(product, sku)}>
                  <ShoppingBag size={15} /> {addingSkuId === sku?.id ? '加入中...' : '加入购物车'}
                </button>
              </div>
            </div>
          </article>
        );
      })}
    </div>
  );
}

function WorkDetailPage({
  bookshelfLoading,
  work,
  chapters,
  isBookmarked,
  onBack,
  onOpenChapter,
  onPurchaseChapter,
  onToggleBookshelf
}: {
  bookshelfLoading: boolean;
  work: Work | null;
  chapters: Chapter[];
  isBookmarked: boolean;
  onBack: () => void;
  onOpenChapter: (chapter: Chapter) => void;
  onPurchaseChapter: (chapter: Chapter) => void;
  onToggleBookshelf: (work: Work) => void;
}) {
  if (!work) return <div className="detailPage"><div className="emptyState">正在加载作品详情...</div></div>;

  return (
    <section className="detailPage">
      <button className="backButton" type="button" onClick={onBack}>返回分区</button>
      <div className="detailTitle">
        <div>
          <p className="eyebrow">{work.workType} · {work.category}</p>
          <h1>{work.title}</h1>
          <p>{work.description}</p>
        </div>
        <div className="detailActions">
          <button
            className={`bookshelfButton ${isBookmarked ? 'isActive' : ''}`}
            type="button"
            disabled={bookshelfLoading}
            onClick={() => onToggleBookshelf(work)}
          >
            <Bookmark size={16} /> {bookshelfLoading ? '处理中...' : isBookmarked ? '移出书架' : '加入书架'}
          </button>
        </div>
      </div>
      <article className="detailBoard">
        <img src={work.coverUrl} alt={work.title} />
        <div>
          <p className="eyebrow">{work.author}</p>
          <div className="chapterList">
            {chapters.map((chapter) => {
              const readable = chapterIsReadable(chapter);
              return (
                <div className="chapterRow" key={chapter.id}>
                  <span>{chapter.chapterNo.toString().padStart(2, '0')} · {chapter.title}</span>
                  <div>
                    <small className={`chapterAccess ${readable ? 'isReadable' : ''}`}>
                      {readable ? <BookOpen size={12} /> : <Lock size={12} />} {chapterAccessLabel(chapter)}
                    </small>
                    <button onClick={() => onOpenChapter(chapter)}><BookOpen size={15} /> 阅读</button>
                    {!readable && <button className="secondary" onClick={() => onPurchaseChapter(chapter)}><Ticket size={15} /> 兑换</button>}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </article>
    </section>
  );
}

function ReaderPage({
  work,
  chapters,
  currentChapterId,
  reader,
  loading,
  error,
  settings,
  onBack,
  onOpenChapter,
  onPurchaseChapter,
  onSaveProgress,
  onSettingsChange
}: {
  work: Work | null;
  chapters: Chapter[];
  currentChapterId: number;
  reader: ReaderResponse | null;
  loading: boolean;
  error: string;
  settings: ReaderSettings;
  onBack: () => void;
  onOpenChapter: (chapter: Chapter) => void;
  onPurchaseChapter: (chapter: Chapter) => void;
  onSaveProgress: (chapterId: number, progressPercent: number) => void;
  onSettingsChange: (patch: Partial<ReaderSettings>) => void;
}) {
  const currentIndex = chapters.findIndex((chapter) => chapter.id === currentChapterId);
  const currentChapter = currentIndex >= 0 ? chapters[currentIndex] : null;
  const previousChapter = currentIndex > 0 ? chapters[currentIndex - 1] : null;
  const nextChapter = currentIndex >= 0 && currentIndex < chapters.length - 1 ? chapters[currentIndex + 1] : null;
  const title = reader?.title ?? currentChapter?.title ?? '章节阅读';
  const paragraphs = (reader?.text || '').split(/\n+/).map((text) => text.trim()).filter(Boolean);
  const readerStyle = {
    '--reader-font-size': `${settings.fontSize}px`,
    '--reader-line-height': String(settings.lineHeight),
    '--reader-max-width': `${readerWidthPx[settings.width]}px`
  } as CSSProperties;
  const saveProgressRef = useRef(onSaveProgress);
  const progressTargetRef = useRef<HTMLDivElement | null>(null);
  const restoredRef = useRef(false);
  const saveTimerRef = useRef<number | null>(null);
  const lastSavedPercentRef = useRef<number | null>(null);

  useEffect(() => {
    saveProgressRef.current = onSaveProgress;
  }, [onSaveProgress]);

  useEffect(() => {
    if (loading || error || !reader) return;
    restoredRef.current = false;
    const savedPercent = normalizeProgressPercent(reader.progressPercent);
    lastSavedPercentRef.current = savedPercent;
    let cancelled = false;
    const restore = () => {
      if (cancelled) return;
      const root = document.documentElement;
      const previousScrollBehavior = root.style.scrollBehavior;
      root.style.scrollBehavior = 'auto';
      window.scrollTo({ top: readerScrollTopForPercent(progressTargetRef.current, savedPercent), behavior: 'auto' });
      root.style.scrollBehavior = previousScrollBehavior;
      restoredRef.current = true;
    };
    const frameId = window.requestAnimationFrame(restore);
    return () => {
      cancelled = true;
      window.cancelAnimationFrame(frameId);
    };
  }, [currentChapterId, error, loading, reader]);

  useEffect(() => {
    if (loading || error || !reader) return;

    const queueSave = () => {
      if (!restoredRef.current) return;
      const percent = readerProgressPercent(progressTargetRef.current);
      if (lastSavedPercentRef.current !== null && Math.abs(percent - lastSavedPercentRef.current) < 2 && percent !== 100) {
        return;
      }
      if (saveTimerRef.current) {
        window.clearTimeout(saveTimerRef.current);
      }
      saveTimerRef.current = window.setTimeout(() => {
        lastSavedPercentRef.current = percent;
        saveProgressRef.current(currentChapterId, percent);
        saveTimerRef.current = null;
      }, 800);
    };

    window.addEventListener('scroll', queueSave, { passive: true });
    window.addEventListener('resize', queueSave);
    return () => {
      window.removeEventListener('scroll', queueSave);
      window.removeEventListener('resize', queueSave);
      if (saveTimerRef.current) {
        window.clearTimeout(saveTimerRef.current);
        saveTimerRef.current = null;
      }
      if (restoredRef.current) {
        const percent = readerProgressPercent(progressTargetRef.current);
        lastSavedPercentRef.current = percent;
        saveProgressRef.current(currentChapterId, percent);
      }
    };
  }, [currentChapterId, error, loading, reader]);

  const saveCurrentProgress = () => {
    if (!reader || !restoredRef.current) return;
    const percent = readerProgressPercent(progressTargetRef.current);
    lastSavedPercentRef.current = percent;
    restoredRef.current = false;
    if (saveTimerRef.current) {
      window.clearTimeout(saveTimerRef.current);
      saveTimerRef.current = null;
    }
    saveProgressRef.current(currentChapterId, percent);
  };

  const openChapterAndSave = (chapter: Chapter) => {
    saveCurrentProgress();
    onOpenChapter(chapter);
  };

  const backAndSave = () => {
    saveCurrentProgress();
    onBack();
  };

  return (
    <section className="readerPage">
      <div className="readerTop">
        <button className="backButton" type="button" onClick={backAndSave}>返回作品</button>
        <div>
          <p className="eyebrow">{work ? `${work.workType} · ${work.title}` : 'READING'}</p>
          <h1>{title}</h1>
        </div>
        <div className="readerNav">
          <button type="button" disabled={!previousChapter} onClick={() => previousChapter && openChapterAndSave(previousChapter)}>
            <ChevronLeft size={16} /> 上一章
          </button>
          <button type="button" disabled={!nextChapter} onClick={() => nextChapter && openChapterAndSave(nextChapter)}>
            下一章 <ChevronRight size={16} />
          </button>
        </div>
      </div>

      <div className="readerLayout">
        <aside className="readerToc" aria-label="章节目录">
          <div className="tocHead">
            <p className="eyebrow">目录</p>
            <small>{currentChapter ? `${currentChapter.chapterNo} / ${chapters.length}` : `${chapters.length} 章`}</small>
          </div>
          <div className="tocList">
            {chapters.map((chapter) => {
              const readable = chapterIsReadable(chapter);
              return (
                <button
                  className={`tocItem ${chapter.id === currentChapterId ? 'active' : ''} ${readable ? 'isReadable' : 'isLocked'}`}
                  key={chapter.id}
                  type="button"
                  onClick={() => openChapterAndSave(chapter)}
                >
                  <span>{chapter.chapterNo.toString().padStart(2, '0')}</span>
                  <b>{chapter.title}</b>
                  <small>
                    {readable ? <BookOpen size={12} /> : <Lock size={12} />} {chapterAccessLabel(chapter)}
                  </small>
                </button>
              );
            })}
          </div>
          <ReaderSettingsPanel settings={settings} onChange={onSettingsChange} />
        </aside>

        <article className={`readerPane readerTheme-${settings.theme}`} style={readerStyle}>
          {loading ? (
            <div className="emptyState">正在加载章节内容...</div>
          ) : error ? (
            <div className="readerLocked">
              <Lock size={24} />
              <h2>章节暂未解锁</h2>
              <p>{currentChapter ? `需要 ${currentChapter.pricePoints} 积分，VIP 可直接阅读。` : error}</p>
              {currentChapter && !currentChapter.free && (
                <button type="button" onClick={() => onPurchaseChapter(currentChapter)}>
                  <Ticket size={16} /> 兑换章节
                </button>
              )}
            </div>
          ) : (
            <div className="readerArticle" ref={progressTargetRef}>
              <header className="readerArticleHeader">
                <p className="eyebrow">{work?.author ?? 'Gray Shelf'}</p>
                <h2>{title}</h2>
              </header>
              {reader?.images?.length ? (
                <div className="readerImages">
                  {reader.images.map((image) => <img src={image} alt={`${title} 漫画页`} key={image} />)}
                </div>
              ) : (
                <div className="readerBody">
                  {paragraphs.length ? paragraphs.map((paragraph, index) => <p key={`${paragraph}-${index}`}>{paragraph}</p>) : <p>该章节暂时没有正文内容。</p>}
                </div>
              )}
            </div>
          )}

          <footer className="readerFooter">
            <button type="button" disabled={!previousChapter} onClick={() => previousChapter && openChapterAndSave(previousChapter)}>
              <ChevronLeft size={16} /> 上一章
            </button>
            <button type="button" onClick={backAndSave}>目录详情</button>
            <button type="button" disabled={!nextChapter} onClick={() => nextChapter && openChapterAndSave(nextChapter)}>
              下一章 <ChevronRight size={16} />
            </button>
          </footer>
        </article>
      </div>
    </section>
  );
}

function ReaderSettingsPanel({
  settings,
  onChange
}: {
  settings: ReaderSettings;
  onChange: (patch: Partial<ReaderSettings>) => void;
}) {
  return (
    <div className="readerSettingsPanel" aria-label="阅读设置">
      <div className="readerToolHead">
        <SlidersHorizontal size={15} />
        <span>阅读设置</span>
      </div>

      <label className="readerRange">
        <span>字号</span>
        <input
          type="range"
          min={16}
          max={26}
          step={1}
          value={settings.fontSize}
          onChange={(event) => onChange({ fontSize: Number(event.target.value) })}
        />
        <b>{settings.fontSize}</b>
      </label>

      <label className="readerRange">
        <span>行距</span>
        <input
          type="range"
          min={1.6}
          max={2.4}
          step={0.1}
          value={settings.lineHeight}
          onChange={(event) => onChange({ lineHeight: Number(event.target.value) })}
        />
        <b>{settings.lineHeight.toFixed(1)}</b>
      </label>

      <div className="readerSegment" aria-label="背景">
        <span>背景</span>
        <div className="readerSegmentButtons">
          {readerThemeOptions.map((option) => (
            <button
              key={option.value}
              type="button"
              className={settings.theme === option.value ? 'isActive' : ''}
              aria-pressed={settings.theme === option.value}
              onClick={() => onChange({ theme: option.value })}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>

      <div className="readerSegment" aria-label="宽度">
        <span>宽度</span>
        <div className="readerSegmentButtons">
          {readerWidthOptions.map((option) => (
            <button
              key={option.value}
              type="button"
              className={settings.width === option.value ? 'isActive' : ''}
              aria-pressed={settings.width === option.value}
              onClick={() => onChange({ width: option.value })}
            >
              {option.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

function CartPanel({
  checkoutLoading,
  discountCents,
  isVip,
  itemMessages,
  items,
  message,
  originalTotalCents,
  selectedItemCount,
  selectedSkuIds,
  totalCents,
  onBrowseMemberGoods,
  onChangeQuantity,
  onCheckout,
  onConfirmPayment,
  onClose,
  onRemove,
  onRemoveSelected,
  onToggleAll,
  onToggleItem,
  paymentLoading,
  pendingPayment
}: {
  checkoutLoading: boolean;
  discountCents: number;
  isVip: boolean;
  itemMessages: Record<number, string>;
  items: CartLine[];
  message: string;
  originalTotalCents: number;
  selectedItemCount: number;
  selectedSkuIds: Set<number>;
  totalCents: number;
  onBrowseMemberGoods: () => void;
  onChangeQuantity: (skuId: number, quantity: number) => void;
  onCheckout: () => void;
  onConfirmPayment: () => void;
  onClose: () => void;
  onRemove: (skuId: number) => void;
  onRemoveSelected: () => void;
  onToggleAll: () => void;
  onToggleItem: (skuId: number) => void;
  paymentLoading: boolean;
  pendingPayment: PendingPayment | null;
}) {
  const selectedLineCount = items.filter((item) => selectedSkuIds.has(item.skuId)).length;
  const allSelected = items.length > 0 && selectedLineCount === items.length;
  const partiallySelected = selectedLineCount > 0 && !allSelected;

  return (
    <div className="cartLayer" onMouseDown={(event) => event.currentTarget === event.target && onClose()}>
      <aside className="cartPanel" role="dialog" aria-modal="true" aria-labelledby="cart-title">
        <header className="cartHeader">
          <div>
            <p className="eyebrow">MEMBER GOODS</p>
            <h2 id="cart-title">购物车</h2>
          </div>
          <button className="modalClose" type="button" aria-label="关闭购物车" onClick={onClose}>
            <X size={18} />
          </button>
        </header>

        {message && <p className="cartMessage">{message}</p>}

        {items.length ? (
          <>
            <div className="cartList">
              {items.map((item) => {
                const unitPriceCents = cartUnitPriceCents(item, isVip);
                const lineOriginalCents = item.originalPriceCents * item.quantity;
                const lineTotalCents = unitPriceCents * item.quantity;
                const lineDiscountCents = cartLineDiscountCents(item, isVip) * item.quantity;
                const itemMessage = itemMessages[item.skuId];
                const selected = selectedSkuIds.has(item.skuId);

                return (
                  <article className={`cartItem${itemMessage ? ' hasItemMessage' : ''}${selected ? ' isSelected' : ''}`} key={cartLineKey(item)}>
                    <button
                      className={`cartSelectCircle${selected ? ' isSelected' : ''}`}
                      type="button"
                      aria-label={selected ? `取消选择 ${item.title}` : `选择 ${item.title}`}
                      aria-pressed={selected}
                      onClick={() => onToggleItem(item.skuId)}
                    />
                    <img src={item.coverUrl} alt={item.title} />
                    <div className="cartItemMain">
                      <p className="eyebrow">{item.productType}{item.limited ? ' · LIMITED' : ''}</p>
                      <h3>{item.title}</h3>
                      <small>{item.skuName} · 单价 {money(unitPriceCents)}</small>
                      {lineDiscountCents > 0 && <small className="cartDiscount">VIP优惠 -{money(lineDiscountCents)}</small>}
                      {itemMessage && <p className="cartItemMessage">{itemMessage}</p>}
                      <div className="cartItemControls">
                        <button
                          type="button"
                          aria-label={`减少 ${item.title} 数量`}
                          onClick={() => onChangeQuantity(item.skuId, item.quantity - 1)}
                        >
                          <Minus size={14} />
                        </button>
                        <span>{item.quantity}</span>
                        <button
                          type="button"
                          aria-label={`增加 ${item.title} 数量`}
                          disabled={item.quantity >= cartQuantityLimit(item)}
                          onClick={() => onChangeQuantity(item.skuId, item.quantity + 1)}
                        >
                          <Plus size={14} />
                        </button>
                        <button type="button" className="cartRemove" aria-label={`移除 ${item.title}`} onClick={() => onRemove(item.skuId)}>
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </div>
                    <div className="cartItemPrice">
                      {lineDiscountCents > 0 && <del>{money(lineOriginalCents)}</del>}
                      <b>{money(lineTotalCents)}</b>
                    </div>
                  </article>
                );
              })}
            </div>

            {pendingPayment && (
              <PaymentPrompt
                loading={paymentLoading}
                payment={pendingPayment}
                onConfirm={onConfirmPayment}
              />
            )}

            <footer className="cartFooter">
              <div className="cartBulkActions">
                <button
                  className={`cartSelectAllButton${allSelected ? ' isSelected' : ''}${partiallySelected ? ' isPartial' : ''}`}
                  type="button"
                  aria-label={allSelected ? '取消全选购物车商品' : '全选购物车商品'}
                  aria-pressed={allSelected}
                  disabled={!items.length}
                  onClick={onToggleAll}
                >
                  <span>全选</span>
                  <span className={`cartSelectCircle${allSelected ? ' isSelected' : ''}${partiallySelected ? ' isPartial' : ''}`} aria-hidden="true" />
                </button>
                {selectedLineCount > 0 && (
                  <button type="button" className="cartRemoveSelected" onClick={onRemoveSelected}>删除所选</button>
                )}
              </div>
              <div className="cartSummary">
                {discountCents > 0 && <small>原价 {money(originalTotalCents)} · VIP优惠 -{money(discountCents)}</small>}
                <span>已选 {selectedItemCount} 件 · 合计</span>
                <strong>{money(totalCents)}</strong>
              </div>
              <button type="button" disabled={!selectedLineCount || checkoutLoading} onClick={onCheckout}>
                {checkoutLoading ? '结算中...' : '结算'}
              </button>
            </footer>
          </>
        ) : (
          <div className="cartEmpty">
            <ShoppingBag size={26} />
            <h3>购物车还是空的</h3>
            <button type="button" onClick={onBrowseMemberGoods}>去逛会员购</button>
          </div>
        )}
      </aside>
    </div>
  );
}

function AccountCenterPage({
  bookshelf,
  cancellingOrderNo,
  error,
  isVip,
  ledger,
  loading,
  mode,
  orderFilter,
  orders,
  payingOrderNo,
  profile,
  readingProgress,
  onBack,
  onCancelOrder,
  onContinueReading,
  onFilterChange,
  onLogin,
  onOpenBookshelf,
  onOpenOrders,
  onOpenWork,
  onPayOrder,
  onRecharge
}: {
  bookshelf: BookshelfItem[];
  cancellingOrderNo: string;
  error: string;
  isVip: boolean;
  ledger: PointsLedgerView[];
  loading: boolean;
  mode: 'home' | 'bookshelf' | 'orders';
  orderFilter: AccountOrderFilter;
  orders: OrderView[];
  payingOrderNo: string;
  profile: Profile | null;
  readingProgress: ReadingProgress[];
  onBack: () => void;
  onCancelOrder: (order: OrderView) => void;
  onContinueReading: (item: ReadingProgress) => void;
  onFilterChange: (filter: AccountOrderFilter) => void;
  onLogin: () => void;
  onOpenBookshelf: () => void;
  onOpenOrders: () => void;
  onOpenWork: (workId: number) => void;
  onPayOrder: (order: OrderView) => void;
  onRecharge: () => void;
}) {
  const [ledgerOpen, setLedgerOpen] = useState(false);
  const [selectedOrderNo, setSelectedOrderNo] = useState('');
  const [bookshelfSort, setBookshelfSort] = useState<BookshelfSort>('recentReading');
  const selectedOrder = useMemo(
    () => orders.find((order) => order.orderNo === selectedOrderNo) ?? null,
    [orders, selectedOrderNo]
  );
  const sortedBookshelf = useMemo(() => {
    const next = [...bookshelf];
    next.sort((left, right) => {
      const leftDate = bookshelfSort === 'recentAdded' ? left.addedAt : left.updatedAt;
      const rightDate = bookshelfSort === 'recentAdded' ? right.addedAt : right.updatedAt;
      return dateValue(rightDate) - dateValue(leftDate);
    });
    return next;
  }, [bookshelf, bookshelfSort]);
  const paidOrders = orders.filter((order) => order.status === 'PAID').length;
  const latestProgress = readingProgress[0] ?? null;
  const pageTitle = mode === 'bookshelf' ? '我的书架' : mode === 'orders' ? '我的订单' : '个人中心';

  useEffect(() => {
    if (selectedOrderNo && !selectedOrder) {
      setSelectedOrderNo('');
    }
  }, [selectedOrderNo, selectedOrder]);

  useEffect(() => {
    if (mode !== 'orders' && selectedOrderNo) {
      setSelectedOrderNo('');
    }
  }, [mode, selectedOrderNo]);

  return (
    <section className={`accountPage accountMode-${mode}`}>
      <button className="backButton" type="button" onClick={onBack}>{mode === 'home' ? '返回首页' : '返回个人中心'}</button>

      <header className="accountHero">
        <div>
          <h1>{pageTitle}</h1>
        </div>
        {profile ? (
          <button type="button" onClick={onRecharge}>
            <Coins size={16} /> 充值 / VIP
          </button>
        ) : (
          <button type="button" onClick={onLogin}>
            <LogIn size={16} /> 登录
          </button>
        )}
      </header>

      {error && <p className="accountNotice">{error}</p>}

      {profile ? (
        <>
          <section className="accountStats">
            <article className="accountStatCard">
              <span>昵称</span>
              <b>{profile.username}</b>
              <small>{profile.email}</small>
            </article>
            <article className="accountStatCard accountPointsCard">
              <span>积分余额</span>
              <b>{profile.points}</b>
              <small>可用于兑换付费章节</small>
              <button type="button" className="accountTextButton" onClick={() => setLedgerOpen(true)}>详情</button>
            </article>
            <article className={`accountStatCard accountVipStat ${isVip ? '' : 'isInactive'}`}>
              <span>会员状态</span>
              {isVip ? (
                <>
                  <b>VIP</b>
                  <small>{profile.vipUntil ? `有效期至 ${shortDateTime(profile.vipUntil)}` : 'VIP 已开通'}</small>
                </>
              ) : (
                <button type="button" className="accountRechargeButton" onClick={onRecharge}>去充值</button>
              )}
            </article>
            <article className="accountStatCard">
              <span>订单</span>
              <b>{orders.length}</b>
              <small>{paidOrders} 个已完成</small>
            </article>
          </section>

          {mode === 'home' && (
            <section className="accountEntryGrid" aria-label="个人中心入口">
              <button type="button" className="accountEntryCard" onClick={onOpenBookshelf}>
                <Bookmark size={22} />
                <span>
                  <b>我的书架</b>
                  <small>{bookshelf.length ? `${bookshelf.length} 部作品` : '查看收藏作品'}</small>
                </span>
                <ChevronRight size={18} />
              </button>
              <button type="button" className="accountEntryCard" onClick={onOpenOrders}>
                <Ticket size={22} />
                <span>
                  <b>我的订单</b>
                  <small>{orders.length ? `${orders.length} 个订单` : '查看购买记录'}</small>
                </span>
                <ChevronRight size={18} />
              </button>
            </section>
          )}

          <section className="accountLibraryGrid">
            <article className="accountPanel accountContinuePanel">
              <div className="accountPanelHead">
                <div>
                  <h2>继续阅读</h2>
                </div>
              </div>
              {latestProgress ? (
                <button type="button" className="accountContinueCard" onClick={() => onContinueReading(latestProgress)}>
                  <img src={latestProgress.coverUrl} alt={latestProgress.title} />
                  <span>
                    <small>{latestProgress.workType} · {latestProgress.category}</small>
                    <b>{latestProgress.title}</b>
                    <em>{latestProgress.chapterNo.toString().padStart(2, '0')} · {latestProgress.chapterTitle} · {progressPercentText(latestProgress.progressPercent)}</em>
                    <strong>继续阅读</strong>
                  </span>
                </button>
              ) : (
                <div className="accountEmpty">暂无阅读记录。</div>
              )}
            </article>

            <article className="accountPanel accountBookshelfPanel">
              <div className="accountPanelHead">
                <div>
                  <h2>我的书架</h2>
                </div>
                <div className="accountFilterBar bookshelfSortBar" aria-label="书架排序">
                  {bookshelfSortOptions.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      className={bookshelfSort === option.value ? 'isActive' : ''}
                      aria-pressed={bookshelfSort === option.value}
                      onClick={() => setBookshelfSort(option.value)}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>
              {bookshelf.length ? (
                <div className="accountBookshelfList">
                  {sortedBookshelf.map((item) => (
                    <button type="button" className="accountBookshelfItem" key={item.workId} onClick={() => onOpenWork(item.workId)}>
                      <img src={item.coverUrl} alt={item.title} />
                      <span>
                        <small>{item.workType} · {item.category}</small>
                        <b>{item.title}</b>
                        <em>{item.lastChapterId ? `读到 ${item.lastChapterNo?.toString().padStart(2, '0')} · ${item.lastChapterTitle} · ${progressPercentText(item.progressPercent)}` : '尚未开始阅读'}</em>
                      </span>
                    </button>
                  ))}
                </div>
              ) : (
                <div className="accountEmpty">书架是空的。</div>
              )}
            </article>
          </section>

          <section className="accountGrid accountGridSingle">
            <article className="accountPanel accountPanelWide">
              <div className="accountPanelHead">
                <div>
                  <h2>我的订单</h2>
                </div>
                <div className="accountOrderTools">
                  {loading && <span>加载中...</span>}
                  <div className="accountFilterBar" aria-label="订单筛选">
                    {accountOrderFilters.map((filter) => (
                      <button
                        key={filter.value}
                        type="button"
                        className={orderFilter === filter.value ? 'isActive' : ''}
                        aria-pressed={orderFilter === filter.value}
                        onClick={() => onFilterChange(filter.value)}
                      >
                        {filter.label}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
              {orders.length ? (
                <div className="accountOrderList">
                  {orders.map((order) => {
                    const itemSummary = order.items
                      .map((item) => item.quantity > 1 ? `${item.title} x${item.quantity}` : item.title)
                      .join('、') || order.orderNo;
                    const orderTitle = order.items[0]?.title ?? order.orderNo;
                    const showItemSummary = itemSummary !== orderTitle;
                    const canPay = canPayOrder(order);
                    const canCancel = canCancelOrder(order);
                    const paying = payingOrderNo === order.orderNo;
                    const cancelling = cancellingOrderNo === order.orderNo;
                    return (
                      <div className="accountOrder" key={order.orderNo}>
                        <div>
                          <p className="eyebrow">{orderTypeLabel(order.orderType)}</p>
                          <h3>{orderTitle}</h3>
                          {showItemSummary && <small>{itemSummary}</small>}
                          <div className="accountPaymentInline">
                            {order.paymentNo ? (
                              <>
                                <span>支付号 {order.paymentNo}</span>
                                <span>支付 · {paymentStatusLabel(order.paymentStatus)}</span>
                                <span>{order.paidAt ? `确认 ${shortDateTime(order.paidAt)}` : '未确认支付'}</span>
                              </>
                            ) : (
                              <span>无需在线支付</span>
                            )}
                          </div>
                        </div>
                        <div className="accountOrderMeta">
                          <b>{orderAmountText(order)}</b>
                          <span className={`accountStatusPill status-${order.status.toLowerCase()}`}>{orderStatusLabel(order.status)}</span>
                          <small>{shortDateTime(order.createdAt)}</small>
                          <div className="accountOrderActions">
                            <button
                              type="button"
                              className="accountDetailButton"
                              onClick={() => setSelectedOrderNo(order.orderNo)}
                            >
                              <Ticket size={14} /> 详情
                            </button>
                            {canPay && (
                              <button
                                type="button"
                                className="accountPayButton"
                                disabled={Boolean(payingOrderNo || cancellingOrderNo)}
                                onClick={() => onPayOrder(order)}
                              >
                                {paying ? '支付中...' : '支付'}
                              </button>
                            )}
                            {canCancel && (
                              <button
                                type="button"
                                className="accountCancelButton"
                                disabled={Boolean(payingOrderNo || cancellingOrderNo)}
                                onClick={() => onCancelOrder(order)}
                              >
                                <X size={14} /> {cancelling ? '取消中...' : '取消'}
                              </button>
                            )}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="accountEmpty">暂无订单。</div>
              )}
            </article>
          </section>

          {selectedOrder && (
            <OrderDetailDialog
              cancelling={cancellingOrderNo === selectedOrder.orderNo}
              disabled={Boolean(payingOrderNo)}
              hasBusyOrder={Boolean(payingOrderNo || cancellingOrderNo)}
              order={selectedOrder}
              paying={payingOrderNo === selectedOrder.orderNo}
              onCancel={() => onCancelOrder(selectedOrder)}
              onClose={() => setSelectedOrderNo('')}
              onPay={() => onPayOrder(selectedOrder)}
            />
          )}

          {ledgerOpen && (
            <div className="modalLayer" onMouseDown={(event) => event.currentTarget === event.target && setLedgerOpen(false)}>
              <section className="pointsLedgerDialog" role="dialog" aria-modal="true" aria-labelledby="points-ledger-title">
                <button className="modalClose" type="button" aria-label="关闭积分详情" onClick={() => setLedgerOpen(false)}>
                  <X size={20} />
                </button>
                <h2 id="points-ledger-title">积分详情</h2>
                {ledger.length ? (
                  <div className="accountMiniList">
                    {ledger.map((item) => (
                      <div className="accountMiniItem" key={item.id}>
                        <span>{ledgerReasonLabel(item.reason)}</span>
                        <b className={item.amount >= 0 ? 'pointsPositive' : 'pointsNegative'}>{signedPoints(item.amount)}</b>
                        <small>{shortDateTime(item.createdAt)}</small>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div className="accountEmpty">暂无积分明细。</div>
                )}
              </section>
            </div>
          )}
        </>
      ) : (
        <div className="accountEmpty accountLoginPrompt">
          <UserRound size={28} />
          <h2>登录后查看个人中心</h2>
          <button type="button" onClick={onLogin}>登录</button>
        </div>
      )}
    </section>
  );
}

function OrderDetailDialog({
  cancelling,
  disabled,
  hasBusyOrder,
  order,
  paying,
  onCancel,
  onClose,
  onPay
}: {
  cancelling: boolean;
  disabled: boolean;
  hasBusyOrder: boolean;
  order: OrderView;
  paying: boolean;
  onCancel: () => void;
  onClose: () => void;
  onPay: () => void;
}) {
  const canPay = canPayOrder(order);
  const canCancel = canCancelOrder(order);
  const items = orderDisplayItems(order);

  return (
    <div className="modalLayer" onMouseDown={(event) => event.currentTarget === event.target && onClose()}>
      <section className="orderDetailDialog" role="dialog" aria-modal="true" aria-labelledby="order-detail-title">
        <button className="modalClose" type="button" aria-label="关闭订单详情" onClick={onClose}>
          <X size={20} />
        </button>
        <header className="orderDetailHead">
          <div>
            <p className="eyebrow">{orderTypeLabel(order.orderType)}</p>
            <h2 id="order-detail-title">订单详情</h2>
          </div>
          <span className={`accountStatusPill status-${order.status.toLowerCase()}`}>{orderStatusLabel(order.status)}</span>
        </header>

        <dl className="orderDetailMeta">
          <div>
            <dt>订单号</dt>
            <dd>{order.orderNo}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{shortDateTime(order.createdAt)}</dd>
          </div>
          <div>
            <dt>支付状态</dt>
            <dd>{paymentStatusLabel(order.paymentStatus)}</dd>
          </div>
          {order.paymentNo && (
            <div>
              <dt>支付号</dt>
              <dd>{order.paymentNo}</dd>
            </div>
          )}
          {order.paidAt && (
            <div>
              <dt>支付时间</dt>
              <dd>{shortDateTime(order.paidAt)}</dd>
            </div>
          )}
        </dl>

        <div className="orderDetailItems">
          <h3>明细</h3>
          {items.map((item, index) => (
            <div className="orderDetailItem" key={`${item.itemType}-${item.refId ?? item.skuId ?? index}`}>
              <div>
                <span>{orderItemTypeLabel(item.itemType)}</span>
                <b>{item.title}</b>
                <small>{orderItemUnitText(item)} · x{item.quantity}</small>
              </div>
              <strong>{orderItemSubtotalText(item)}</strong>
            </div>
          ))}
        </div>

        <footer className="orderDetailFooter">
          <div>
            <span>合计</span>
            <b>{orderAmountText(order)}</b>
          </div>
          {(canPay || canCancel) && (
            <div className="orderDetailActions">
              {canPay && (
                <button type="button" disabled={hasBusyOrder || disabled} onClick={onPay}>
                  <Sparkles size={16} /> {paying ? '支付中...' : '支付'}
                </button>
              )}
              {canCancel && (
                <button type="button" className="accountCancelButton" disabled={hasBusyOrder} onClick={onCancel}>
                  <X size={15} /> {cancelling ? '取消中...' : '取消订单'}
                </button>
              )}
            </div>
          )}
        </footer>
      </section>
    </div>
  );
}

function PaymentPrompt({
  loading,
  payment,
  onConfirm
}: {
  loading: boolean;
  payment: PendingPayment;
  onConfirm: () => void;
}) {
  return (
    <section className="paymentPrompt" aria-label="订单支付确认">
      <div>
        <h3>订单待支付</h3>
      </div>
      {DEMO_PAYMENT_ENABLED && <p className="demoPaymentNotice">演示支付，不会产生真实扣款。</p>}
      <dl className="paymentMeta">
        <div>
          <dt>订单号</dt>
          <dd>{payment.orderNo}</dd>
        </div>
        <div>
          <dt>支付号</dt>
          <dd>{payment.paymentNo}</dd>
        </div>
        <div>
          <dt>应付</dt>
          <dd>{money(payment.amountCents)}</dd>
        </div>
      </dl>
      <button type="button" disabled={loading} onClick={onConfirm}>
        <Sparkles size={16} /> {loading ? '确认中...' : DEMO_PAYMENT_ENABLED ? '模拟支付' : '前往支付'}
      </button>
    </section>
  );
}

function ProductDetailPage({
  addingSkuId,
  product,
  onAddToCart,
  onBack
}: {
  addingSkuId: number | null;
  product: Product | null;
  onAddToCart: (product: Product, sku?: ProductSku) => void;
  onBack: () => void;
}) {
  if (!product) return <div className="detailPage"><div className="emptyState">正在加载商品详情...</div></div>;
  const primarySku = product.skus[0];

  return (
    <section className="detailPage">
      <button className="backButton" type="button" onClick={onBack}>返回分区</button>
      <article className="productDetail">
        <img src={product.coverUrl} alt={product.title} />
        <div>
          <p className="eyebrow">{product.productType}{product.limited ? ' · LIMITED' : ''}</p>
          <h1>{product.title}</h1>
          <p>{product.description}</p>
          <div className="skuList">
            {product.skus.map((sku) => (
              <div className="skuRow" key={sku.id}>
                <span>{sku.skuName}</span>
                <div className="skuPrice">
                  <b>{money(sku.priceCents)}</b>
                  {hasVipDiscount(sku.priceCents, sku.vipPriceCents) && (
                    <small>{vipDiscountText(sku.priceCents, sku.vipPriceCents)}</small>
                  )}
                </div>
              </div>
            ))}
          </div>
          <button type="button" disabled={!primarySku || addingSkuId === primarySku.id} onClick={() => onAddToCart(product, primarySku)}>
            <ShoppingBag size={16} /> {addingSkuId === primarySku?.id ? '加入中...' : '加入购物车'}
          </button>
        </div>
      </article>
    </section>
  );
}
