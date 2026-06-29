# Gray Project Context

更新时间：2026-06-28

## 使用约定

- 用户要求：从现在开始，每当窗口上下文被压缩、或我察觉到上下文即将丢失/已经被摘要替换时，把重要内容整理进本文件。
- 位置：项目根目录 `CONTEXT.md`。
- 本文件用于让后续会话快速接上，不替代 README、代码、计划文档或实际提交记录。

## 项目概况

- 项目：二次元综合电商平台 MVP。
- 用户端：React + TypeScript，位于 `frontend/web`。
- 后台端：React + TypeScript，位于 `frontend/admin`。
- 后端：Spring Boot 3.5.x + Spring Cloud 2025.0.x + Spring Cloud Alibaba/Nacos/Sentinel。
- 部署：Docker Compose 单机部署。
- 数据与中间件：MySQL、Redis、RabbitMQ、MinIO、Nacos。
- 内容来源原则：支持后台录入/JSON/CSV/Excel 导入授权内容；不实现盗版正文/漫画页抓取。

## 当前核心体验

- 用户端顶部主分区：`轻小说`、`漫画`、`会员购`。
- 默认进入轻小说分区。
- 搜索必须先属于当前分区：
  - 轻小说/漫画走 `/api/v1/works?type=NOVEL|MANGA&page=1&size=10&keyword=...`
  - 会员购走 `/api/v1/products?page=1&size=10&keyword=...`
- 搜索结果纵向排列，每页最多 10 条，显示封面、标题、简介。
- 点击作品封面或简介进入 `/works/{id}`。
- 点击商品封面或简介进入 `/products/{id}`。
- 作品详情页不再显示黑色正文预览框。
- 点击章节“阅读”进入独立阅读页：`/works/{workId}/chapters/{chapterId}/read`。
- 阅读页包含章节目录、正文区域、上一章/下一章、返回作品、目录详情。
- 未登录读取付费章节时，前端直接显示“章节暂未解锁”和“兑换章节”，避免控制台出现无意义接口错误。
- 用户端分区页顶部黄色状态提示条已移除，不再展示“已切换到.../未登录”这类横幅提示。
- 用户端中部三块大分区选择栏已移除；分区切换只保留顶部导航，搜索栏直接跟在 hero 后面。
- 用户端首页 hero 只保留英文分区 eyebrow 和 `Gray Shelf` 标题，不显示中文说明段落。
- 右上角账号菜单支持 hover 展开，鼠标从“登录/头像”移动到下拉菜单时不应立即消失；通过短延迟关闭和隐形 hover 桥保证可点击。
- 登录/注册弹窗为邮箱账号面板；不做二维码、短信或第三方登录。视觉要贴近站点整体的纸张底色、黑色线框和红色硬投影风格。
- 用户端不显示“每页最多 10 个”这类实现说明，分页逻辑保留但不作为提示文案展示。

## 最近重要文件

- `frontend/web/src/App.tsx`
  - 用户端主要页面、路由、账号弹窗、分区搜索、作品/商品详情、阅读页逻辑。
- `frontend/web/src/styles.css`
  - 用户端视觉样式，包括分区搜索、结果列表、详情页、阅读器布局。
- `backend/content-service/src/main/java/com/gray/anime/content/application/ContentApplicationService.java`
  - 作品分页、搜索、章节阅读权限。
- `backend/shop-service/src/main/java/com/gray/anime/shop/application/ShopApplicationService.java`
  - 商品分页、搜索、详情。
- `backend/shop-service/src/main/java/com/gray/anime/shop/interfaces/ShopController.java`
  - 商品公开接口。
- `pom.xml`
  - Maven 版本和 MyBatis-Plus 分页相关依赖管理。
- `docker-compose.yml`
  - 本地容器编排。

## 本地运行状态

最近一次已执行：

```powershell
docker compose up -d
```

访问地址：

- 用户端：http://127.0.0.1/
- 网关/API：http://127.0.0.1:8080
- Nacos：http://127.0.0.1:8848/nacos
- RabbitMQ：http://127.0.0.1:15672
- MinIO：http://127.0.0.1:9001

最近验证：

- 前端首页 `200 OK`。
- `/api/v1/works?type=NOVEL&page=1&size=1` 返回正常 JSON。
- MySQL 为 healthy。
- `gateway-service` 和 `content-service` 已在 Nacos 注册成功。

## 启动注意事项

- 如果 Docker Desktop 没启动，`docker compose up -d` 会报找不到 `dockerDesktopLinuxEngine`。
- Nacos 首次启动较慢，Spring 服务可能因为 `Client not connected, current status: STARTING` 注册失败。
- 解决方式：等 Nacos 完全启动后重启 Spring 服务：

```powershell
docker compose restart gateway content-service user-service shop-service inventory-service order-service payment-service ingestion-service
```

- PowerShell 里 URL 参数包含 `&` 时必须加引号，例如：

```powershell
curl.exe -s "http://127.0.0.1/api/v1/works?type=NOVEL&page=1&size=1"
```

## 构建命令

前端构建：

```powershell
npm run build -w frontend/web
```

后端 Maven 打包：

```powershell
mvn -q "-Dmaven.repo.local=C:\Users\16076\Documents\Gray\.m2\repository" -DskipTests package
```

重建前端容器：

```powershell
docker compose up --build -d frontend
```

重建核心服务：

```powershell
docker compose up --build -d frontend gateway content-service shop-service
```

## 后续偏好

- 用户希望更真实的用户体验，避免“演示按钮/假框/黑框预览”式界面。
- 前端应继续遵循 `frontend-design` 和 `frontend-ui-ux` 的要求；实际可用优先，不做空洞说明页。
- 用户端不显示后台跳转入口。
- VIP、每日签到放在右上角头像菜单。
- 登录按钮打开登录/注册弹窗，不保留演示账号快捷入口。
- VIP 下单只创建订单并显示支付号，不自动模拟支付成功。
- 章节权限、VIP 折扣、限量规则在用户实际购买/兑换/下单时提示，不放在搜索侧栏里。

## 下次接手建议

- 如继续做阅读器，可补：字体大小、行距、阅读主题、滚动进度、漫画分页/双页模式、章节购买后自动刷新阅读权限。
- 如继续做商品页，可补：SKU 选择、库存状态、加入购物车、限时开售倒计时。
- 如继续做后台，可补：作品/章节导入、商品维护、订单看板、日流量和营业额统计。
