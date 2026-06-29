import { BarChart3, Boxes, Database, LayoutDashboard, PackagePlus, RefreshCcw, Search, ShieldCheck, UsersRound } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

type ApiResponse<T> = { code: string; message: string; data: T; traceId: string };
type PageResult<T> = { items: T[]; page: number; size: number; total: number };
type Metrics = { ordersToday: number; paidOrdersToday: number; revenueCentsToday: number; vipRevenueCentsToday: number; visitorsToday: number };
type User = { id: number; email: string; username: string; roles: string[]; status: string; points: number; vipUntil: string | null };
type Order = { id: number; orderNo: string; orderType: string; totalCents: number; totalPoints: number; status: string; paymentNo: string | null; createdAt: string };
type Work = { id: number; title: string; workType: string; author: string; category: string; popularity: number };
type Product = { id: number; title: string; productType: string; limited: boolean; skus: { priceCents: number }[] };
type ImportTask = { id: number; sourceType: string; sourceName: string; status: string; importedWorks: number; importedProducts: number; createdAt: string };

const fallbackMetrics: Metrics = { ordersToday: 18, paidOrdersToday: 15, revenueCentsToday: 98600, vipRevenueCentsToday: 21000, visitorsToday: 1268 };
const fallbackUsers: User[] = [
  { id: 1, email: 'admin@gray.test', username: 'Gray Admin', roles: ['USER', 'VIP', 'ADMIN'], status: 'ACTIVE', points: 300, vipUntil: '2027-06-09T00:00:00' },
  { id: 2, email: 'demo@gray.test', username: 'Demo Reader', roles: ['USER'], status: 'ACTIVE', points: 80, vipUntil: null }
];
const fallbackOrders: Order[] = [
  { id: 1, orderNo: 'OD-DEMO-001', orderType: 'PRODUCT', totalCents: 6120, totalPoints: 0, status: 'PAID', paymentNo: 'PAY-DEMO-001', createdAt: new Date().toISOString() },
  { id: 2, orderNo: 'VIP-DEMO-001', orderType: 'VIP', totalCents: 3000, totalPoints: 0, status: 'PENDING_PAYMENT', paymentNo: 'PAY-DEMO-002', createdAt: new Date().toISOString() }
];
const fallbackWorks: Work[] = [
  { id: 1, title: '星轨书店的魔女', workType: 'NOVEL', author: 'Lumen Circle', category: 'Fantasy', popularity: 932 },
  { id: 2, title: '雨港机械少女', workType: 'MANGA', author: 'North Pier', category: 'Sci-Fi', popularity: 884 }
];
const fallbackProducts: Product[] = [
  { id: 1, title: '实体书限定版', productType: 'BOOK', limited: false, skus: [{ priceCents: 6800 }] },
  { id: 2, title: '雨港机械少女立牌', productType: 'GOODS', limited: true, skus: [{ priceCents: 3900 }] }
];
const fallbackTasks: ImportTask[] = [
  { id: 1, sourceType: 'DEMO', sourceName: 'local-demo-crawler', status: 'SUCCESS', importedWorks: 3, importedProducts: 2, createdAt: new Date().toISOString() }
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
  if (!response.ok) throw new Error(await response.text());
  const body = (await response.json()) as ApiResponse<T>;
  return body.data;
}

function money(cents: number) {
  return `¥${(cents / 100).toFixed(2)}`;
}

export function App() {
  const [token, setToken] = useState('');
  const [metrics, setMetrics] = useState<Metrics>(fallbackMetrics);
  const [users, setUsers] = useState<User[]>(fallbackUsers);
  const [orders, setOrders] = useState<Order[]>(fallbackOrders);
  const [works, setWorks] = useState<Work[]>(fallbackWorks);
  const [products, setProducts] = useState<Product[]>(fallbackProducts);
  const [tasks, setTasks] = useState<ImportTask[]>(fallbackTasks);
  const [notice, setNotice] = useState('登录 admin@gray.test / Admin@123456 后可调用真实后台接口。');

  const revenueSeries = useMemo(() => (
    ['09:00', '11:00', '13:00', '15:00', '17:00', '19:00'].map((time, index) => ({
      time,
      revenue: Math.round(metrics.revenueCentsToday * (0.12 + index * 0.14) / 100)
    }))
  ), [metrics]);

  useEffect(() => {
    if (token) refreshAll();
  }, [token]);

  async function loginAdmin() {
    try {
      const data = await api<{ accessToken: string }>('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email: 'admin@gray.test', password: 'Admin@123456' })
      });
      setToken(data.accessToken);
      setNotice('已登录管理员，后台数据将从 Gateway 拉取。');
    } catch {
      setNotice('后端未启动，当前使用后台本地演示数据。');
    }
  }

  async function refreshAll() {
    try {
      const [m, u, o, w, p, t] = await Promise.all([
        api<Metrics>('/api/v1/admin/dashboard/daily-metrics', {}, token),
        api<PageResult<User>>('/api/v1/admin/users', {}, token),
        api<PageResult<Order>>('/api/v1/admin/orders', {}, token),
        api<PageResult<Work>>('/api/v1/admin/works', {}, token),
        api<PageResult<Product>>('/api/v1/admin/products', {}, token),
        api<PageResult<ImportTask>>('/api/v1/admin/import-tasks', {}, token)
      ]);
      setMetrics(m);
      setUsers(u.items);
      setOrders(o.items);
      setWorks(w.items);
      setProducts(p.items);
      setTasks(t.items);
      setNotice('后台数据已刷新。');
    } catch {
      setNotice('刷新失败，保留本地演示数据。');
    }
  }

  async function importDemo() {
    if (!token) return setNotice('请先登录管理员。');
    try {
      await api('/api/v1/admin/import-tasks/demo', { method: 'POST' }, token);
      await refreshAll();
      setNotice('Demo 内容和商品导入完成。');
    } catch {
      setNotice('导入失败，请确认后端服务已启动。');
    }
  }

  return (
    <main className="adminShell">
      <aside className="sidebar">
        <div className="adminBrand"><ShieldCheck size={20} /> Gray Ops</div>
        <a href="#dashboard"><LayoutDashboard size={17} /> 看板</a>
        <a href="#orders"><BarChart3 size={17} /> 订单</a>
        <a href="#users"><UsersRound size={17} /> 用户</a>
        <a href="#catalog"><Boxes size={17} /> 内容/商品</a>
        <a href="#imports"><Database size={17} /> 导入</a>
      </aside>

      <section className="panelStack">
        <header className="adminTop">
          <div>
            <p className="eyebrow">OPERATIONS CONSOLE</p>
            <h1>今日运营</h1>
          </div>
          <div className="topActions">
            <div className="search"><Search size={16} /><input placeholder="搜索用户、订单、作品" /></div>
            <button onClick={loginAdmin}>管理员登录</button>
            <button className="secondary" onClick={refreshAll}><RefreshCcw size={16} /> 刷新</button>
          </div>
        </header>

        <div className="notice">{notice}</div>

        <section className="metricGrid" id="dashboard">
          <Metric label="今日流量" value={metrics.visitorsToday.toLocaleString()} />
          <Metric label="今日订单" value={metrics.ordersToday.toString()} />
          <Metric label="已支付订单" value={metrics.paidOrdersToday.toString()} />
          <Metric label="今日营业额" value={money(metrics.revenueCentsToday)} />
          <Metric label="VIP收入" value={money(metrics.vipRevenueCentsToday)} />
        </section>

        <section className="chartPanel">
          <div className="sectionHead"><h2>营业额曲线</h2><span>按演示时段聚合</span></div>
          <ResponsiveContainer width="100%" height={260}>
            <AreaChart data={revenueSeries}>
              <defs>
                <linearGradient id="revenue" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#0f766e" stopOpacity={0.45} />
                  <stop offset="95%" stopColor="#0f766e" stopOpacity={0.05} />
                </linearGradient>
              </defs>
              <CartesianGrid stroke="#ded5c7" />
              <XAxis dataKey="time" />
              <YAxis />
              <Tooltip />
              <Area type="monotone" dataKey="revenue" stroke="#0f766e" fill="url(#revenue)" strokeWidth={2} />
            </AreaChart>
          </ResponsiveContainer>
        </section>

        <section className="dataGrid">
          <TablePanel id="orders" title="订单管理" columns={['订单号', '类型', '金额/积分', '状态', '支付号']} rows={orders.map((o) => [o.orderNo, o.orderType, o.totalCents ? money(o.totalCents) : `${o.totalPoints}积分`, o.status, o.paymentNo ?? '-'])} />
          <TablePanel id="users" title="用户管理" columns={['邮箱', '昵称', '角色', '积分', '状态']} rows={users.map((u) => [u.email, u.username, u.roles.join(','), u.points.toString(), u.status])} />
        </section>

        <section className="dataGrid" id="catalog">
          <TablePanel title="作品管理" columns={['标题', '类型', '作者', '分类', '热度']} rows={works.map((w) => [w.title, w.workType, w.author, w.category, String(w.popularity)])} />
          <TablePanel title="商品管理" columns={['商品', '类型', '价格', '限量']} rows={products.map((p) => [p.title, p.productType, money(p.skus[0]?.priceCents ?? 0), p.limited ? '是' : '否'])} />
        </section>

        <section className="importPanel" id="imports">
          <div>
            <p className="eyebrow">INGESTION</p>
            <h2>内容导入任务</h2>
            <p>支持本地 demo crawler、开放版权/API 元数据和后台 JSON 批量导入。</p>
          </div>
          <button onClick={importDemo}><PackagePlus size={16} /> 导入 Demo 数据</button>
          <div className="taskList">
            {tasks.map((task) => (
              <div className="task" key={task.id}>
                <b>{task.sourceName}</b>
                <span>{task.status}</span>
                <span>{task.importedWorks} 作品 / {task.importedProducts} 商品</span>
              </div>
            ))}
          </div>
        </section>
      </section>
    </main>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <article className="metric">
      <span>{label}</span>
      <b>{value}</b>
    </article>
  );
}

function TablePanel({ id, title, columns, rows }: { id?: string; title: string; columns: string[]; rows: string[][] }) {
  return (
    <article className="tablePanel" id={id}>
      <div className="sectionHead"><h2>{title}</h2><span>{rows.length} 条</span></div>
      <div className="tableScroll">
        <table>
          <thead>
            <tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={`${title}-${index}`}>{row.map((cell, cellIndex) => <td key={cellIndex}>{cell}</td>)}</tr>
            ))}
          </tbody>
        </table>
      </div>
    </article>
  );
}
