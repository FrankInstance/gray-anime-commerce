import {
  BookOpen,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Crown,
  Gift,
  Lock,
  LogIn,
  LogOut,
  Search,
  ShoppingBag,
  Sparkles,
  Ticket,
  UserPlus,
  UserRound,
  X
} from 'lucide-react';
import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';

type ApiResponse<T> = { code: string; message: string; data: T; traceId: string };
type PageResult<T> = { items: T[]; page: number; size: number; total: number };
type Work = { id: number; title: string; workType: string; author: string; category: string; description: string; coverUrl: string; popularity: number };
type WorkDetail = { work: Work; chapters: Chapter[] };
type Chapter = { id: number; chapterNo: number; title: string; free: boolean; pricePoints: number };
type ReaderResponse = { chapterId: number; title: string; unlocked: boolean; text: string; images: string[] };
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
type Profile = { id: number; email: string; username: string; roles: string[]; status?: string; points: number; vipUntil: string | null };
type AuthMode = 'login' | 'register';
type AuthResponse = { accessToken: string; expiresIn: number; profile: Profile; roles: string[] };
type SectionKey = 'NOVEL' | 'MANGA' | 'MEMBER';
type AppRoute =
  | { kind: 'list' }
  | { kind: 'work'; id: number }
  | { kind: 'reader'; workId: number; chapterId: number }
  | { kind: 'product'; id: number };

const PAGE_SIZE = 10;

const sections: { key: SectionKey; label: string }[] = [
  { key: 'NOVEL', label: '轻小说' },
  { key: 'MANGA', label: '漫画' },
  { key: 'MEMBER', label: '会员购' }
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

async function api<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.headers ?? {})
    }
  });
  if (!response.ok) {
    const text = await response.text();
    let message = text || response.statusText;
    try {
      const body = JSON.parse(text) as Partial<ApiResponse<unknown>>;
      message = body.message || message;
    } catch {
      // Keep non-JSON errors readable in forms and notices.
    }
    throw new Error(message);
  }
  const body = (await response.json()) as ApiResponse<T>;
  return body.data;
}

function money(cents: number) {
  return `¥${(cents / 100).toFixed(2)}`;
}

function errorMessage(error: unknown) {
  if (error instanceof Error && error.message) {
    return error.message.length > 140 ? '操作失败，请稍后重试。' : error.message;
  }
  return '操作失败，请稍后重试。';
}

function profileWithRoles(data: AuthResponse): Profile {
  return {
    ...data.profile,
    roles: Array.from(data.roles ?? data.profile.roles ?? [])
  };
}

function avatarInitial(profile: Profile | null) {
  return profile?.username.trim().slice(0, 1).toUpperCase() || 'G';
}

function parseRoute(path = window.location.pathname): AppRoute {
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
  const [token, setToken] = useState('');
  const [profile, setProfile] = useState<Profile | null>(null);
  const [, setNotice] = useState('');
  const [accountOpen, setAccountOpen] = useState(false);
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [authEmail, setAuthEmail] = useState('');
  const [authUsername, setAuthUsername] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState('');
  const accountCloseTimer = useRef<number | null>(null);

  const isVip = useMemo(() => profile?.roles.includes('VIP') ?? false, [profile]);
  const activeMeta = sections.find((section) => section.key === activeSection) ?? sections[0];
  const activePage = activeSection === 'MEMBER' ? productPage : workPage;
  const totalPages = Math.max(1, Math.ceil((activePage.total || activePage.items.length) / PAGE_SIZE));

  useEffect(() => {
    const onPopState = () => setRoute(parseRoute());
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  useEffect(() => () => {
    if (accountCloseTimer.current) {
      window.clearTimeout(accountCloseTimer.current);
    }
  }, []);

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
      api<WorkDetail>(`/api/v1/works/${workId}`)
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
              text: '免费章节演示文本：列车驶过星轨，书页像薄雪一样翻飞。\n\n车窗外的废弃轨道铺向夜色深处，少女把书签夹进泛黄的书页，听见远处传来旧书店的铃声。',
              images: []
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

  function navigate(path: string) {
    window.history.pushState(null, '', path);
    setRoute(parseRoute(path));
    window.scrollTo({ top: 0, behavior: 'smooth' });
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

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmittedKeyword(searchDraft.trim());
    setCurrentPage(1);
  }

  function openAuth(mode: AuthMode) {
    setAuthMode(mode);
    setAuthError('');
    setAuthOpen(true);
    setAccountOpen(false);
  }

  function closeAuth() {
    if (!authLoading) {
      setAuthOpen(false);
      setAuthError('');
    }
  }

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAuthLoading(true);
    setAuthError('');
    try {
      const isRegister = authMode === 'register';
      const data = await api<AuthResponse>(isRegister ? '/api/v1/auth/register' : '/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({
          email: authEmail.trim(),
          password: authPassword,
          ...(isRegister ? { username: authUsername.trim() } : {})
        })
      });
      setToken(data.accessToken);
      setProfile(profileWithRoles(data));
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
      setNotice(`兑换成功，消耗 ${chapter.pricePoints} 积分；该章节已永久解锁。`);
      openChapter(chapter);
    } catch {
      setNotice(`兑换失败：该章节需要 ${chapter.pricePoints} 积分。VIP 可免费阅读，普通用户兑换后永久可读。`);
    }
  }

  async function createVipOrder() {
    if (!token) {
      setNotice('请先登录。');
      openAuth('login');
      return;
    }
    try {
      const order = await api<{ paymentNo: string }>('/api/v1/vip/orders', { method: 'POST' }, token);
      setNotice(`VIP 订单已创建，支付号：${order.paymentNo}`);
      setAccountOpen(false);
    } catch (error) {
      setNotice(errorMessage(error));
    }
  }

  function logout() {
    setToken('');
    setProfile(null);
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
            <span className={`avatar ${profile ? 'signed' : ''}`}>
              {profile ? avatarInitial(profile) : <UserRound size={18} />}
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
                <button type="button" role="menuitem" className="dropdownAction" onClick={createVipOrder}>
                  <Crown size={16} /> {isVip ? '续费 VIP 30元/月' : '开通 VIP 30元/月'}
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
              <ProductResultList products={productPage.items} loading={listLoading} onOpen={(product) => navigate(`/products/${product.id}`)} />
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
          chapters={chapters}
          work={detailWork}
          onBack={showList}
          onOpenChapter={openChapter}
          onPurchaseChapter={purchaseChapter}
        />
      ) : route.kind === 'reader' ? (
        <ReaderPage
          chapters={chapters}
          currentChapterId={route.chapterId}
          error={readerError}
          loading={readerLoading}
          reader={readerData}
          work={detailWork}
          onBack={() => navigate(`/works/${route.workId}`)}
          onOpenChapter={openChapter}
          onPurchaseChapter={purchaseChapter}
        />
      ) : (
        <ProductDetailPage product={detailProduct} onBack={showList} />
      )}

      {authOpen && (
        <div className="modalLayer" onMouseDown={(event) => event.currentTarget === event.target && closeAuth()}>
          <section className="authDialog" role="dialog" aria-modal="true" aria-labelledby="auth-title">
            <button className="modalClose" type="button" aria-label="关闭登录注册窗口" onClick={closeAuth}>
              <X size={18} />
            </button>
            <h2 id="auth-title">{authMode === 'login' ? '登录' : '注册'}</h2>
            <form className="authForm" onSubmit={submitAuth}>
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
              <label className="authField">
                <span>密码</span>
                <input
                  autoComplete={authMode === 'login' ? 'current-password' : 'new-password'}
                  required
                  minLength={6}
                  maxLength={64}
                  type="password"
                  value={authPassword}
                  onChange={(event) => setAuthPassword(event.target.value)}
                  placeholder="请输入密码"
                />
              </label>
              {authError && <div className="authError" role="alert">{authError}</div>}
              <div className="authActions">
                <button
                  className="secondary"
                  type="button"
                  onClick={() => { setAuthMode(authMode === 'login' ? 'register' : 'login'); setAuthError(''); }}
                >
                  {authMode === 'login' ? '注册' : '返回登录'}
                </button>
                <button type="submit" disabled={authLoading}>
                  {authMode === 'login' ? <LogIn size={17} /> : <UserPlus size={17} />}
                  {authLoading ? '处理中...' : authMode === 'login' ? '登录' : '注册'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </main>
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

function ProductResultList({ products, loading, onOpen }: { products: Product[]; loading: boolean; onOpen: (product: Product) => void }) {
  if (loading) return <div className="emptyState">正在加载会员购...</div>;
  if (!products.length) return <div className="emptyState">会员购没有匹配结果。</div>;

  return (
    <div className="resultList">
      {products.map((product) => (
        <article className="resultCard" key={product.id}>
          <button className="resultCover" type="button" onClick={() => onOpen(product)} aria-label={`打开${product.title}详情`}>
            <img src={product.coverUrl} alt={product.title} />
          </button>
          <button className="resultCopy" type="button" onClick={() => onOpen(product)}>
            <span className="eyebrow">{product.productType}{product.limited ? ' · LIMITED' : ''}</span>
            <h3>{product.title}</h3>
            <p>{product.description}</p>
            <small>{money(product.skus[0]?.vipPriceCents ?? product.skus[0]?.priceCents ?? 0)}</small>
          </button>
        </article>
      ))}
    </div>
  );
}

function WorkDetailPage({
  work,
  chapters,
  onBack,
  onOpenChapter,
  onPurchaseChapter
}: {
  work: Work | null;
  chapters: Chapter[];
  onBack: () => void;
  onOpenChapter: (chapter: Chapter) => void;
  onPurchaseChapter: (chapter: Chapter) => void;
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
      </div>
      <article className="detailBoard">
        <img src={work.coverUrl} alt={work.title} />
        <div>
          <p className="eyebrow">{work.author}</p>
          <div className="chapterList">
            {chapters.map((chapter) => (
              <div className="chapterRow" key={chapter.id}>
                <span>{chapter.chapterNo.toString().padStart(2, '0')} · {chapter.title}</span>
                <div>
                  {chapter.free ? <small>FREE</small> : <small><Lock size={12} /> {chapter.pricePoints}积分</small>}
                  <button onClick={() => onOpenChapter(chapter)}><BookOpen size={15} /> 阅读</button>
                  {!chapter.free && <button className="secondary" onClick={() => onPurchaseChapter(chapter)}><Ticket size={15} /> 兑换</button>}
                </div>
              </div>
            ))}
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
  onBack,
  onOpenChapter,
  onPurchaseChapter
}: {
  work: Work | null;
  chapters: Chapter[];
  currentChapterId: number;
  reader: ReaderResponse | null;
  loading: boolean;
  error: string;
  onBack: () => void;
  onOpenChapter: (chapter: Chapter) => void;
  onPurchaseChapter: (chapter: Chapter) => void;
}) {
  const currentIndex = chapters.findIndex((chapter) => chapter.id === currentChapterId);
  const currentChapter = currentIndex >= 0 ? chapters[currentIndex] : null;
  const previousChapter = currentIndex > 0 ? chapters[currentIndex - 1] : null;
  const nextChapter = currentIndex >= 0 && currentIndex < chapters.length - 1 ? chapters[currentIndex + 1] : null;
  const title = reader?.title ?? currentChapter?.title ?? '章节阅读';
  const paragraphs = (reader?.text || '').split(/\n+/).map((text) => text.trim()).filter(Boolean);

  return (
    <section className="readerPage">
      <div className="readerTop">
        <button className="backButton" type="button" onClick={onBack}>返回作品</button>
        <div>
          <p className="eyebrow">{work ? `${work.workType} · ${work.title}` : 'READING'}</p>
          <h1>{title}</h1>
        </div>
        <div className="readerNav">
          <button type="button" disabled={!previousChapter} onClick={() => previousChapter && onOpenChapter(previousChapter)}>
            <ChevronLeft size={16} /> 上一章
          </button>
          <button type="button" disabled={!nextChapter} onClick={() => nextChapter && onOpenChapter(nextChapter)}>
            下一章 <ChevronRight size={16} />
          </button>
        </div>
      </div>

      <div className="readerLayout">
        <aside className="readerToc" aria-label="章节目录">
          <p className="eyebrow">目录</p>
          <div className="tocList">
            {chapters.map((chapter) => (
              <button
                className={`tocItem ${chapter.id === currentChapterId ? 'active' : ''}`}
                key={chapter.id}
                type="button"
                onClick={() => onOpenChapter(chapter)}
              >
                <span>{chapter.chapterNo.toString().padStart(2, '0')}</span>
                <b>{chapter.title}</b>
                {!chapter.free && <small><Lock size={12} /> {chapter.pricePoints}积分</small>}
              </button>
            ))}
          </div>
        </aside>

        <article className="readerPane">
          {loading ? (
            <div className="emptyState">正在加载章节内容...</div>
          ) : error ? (
            <div className="readerLocked">
              <Lock size={24} />
              <h2>章节暂未解锁</h2>
              <p>{currentChapter ? `该章节需要 ${currentChapter.pricePoints} 积分兑换，购买后永久可读；VIP 可免费阅读。` : error}</p>
              {currentChapter && !currentChapter.free && (
                <button type="button" onClick={() => onPurchaseChapter(currentChapter)}>
                  <Ticket size={16} /> 兑换章节
                </button>
              )}
            </div>
          ) : (
            <>
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
            </>
          )}

          <footer className="readerFooter">
            <button type="button" disabled={!previousChapter} onClick={() => previousChapter && onOpenChapter(previousChapter)}>
              <ChevronLeft size={16} /> 上一章
            </button>
            <button type="button" onClick={onBack}>目录详情</button>
            <button type="button" disabled={!nextChapter} onClick={() => nextChapter && onOpenChapter(nextChapter)}>
              下一章 <ChevronRight size={16} />
            </button>
          </footer>
        </article>
      </div>
    </section>
  );
}

function ProductDetailPage({ product, onBack }: { product: Product | null; onBack: () => void }) {
  if (!product) return <div className="detailPage"><div className="emptyState">正在加载商品详情...</div></div>;

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
                <b>{money(sku.vipPriceCents || sku.priceCents)}</b>
              </div>
            ))}
          </div>
          <button><ShoppingBag size={16} /> 加入购物车</button>
        </div>
      </article>
    </section>
  );
}
