# Gray Anime Commerce

二次元综合电商平台 MVP：内容阅读、实体商城、VIP/积分、限量抢购、后台运营看板。

## Stack

- Frontend: React + TypeScript + Vite
- Backend: Java 21, Spring Boot 3.5.x, Spring Cloud 2025.0.x, Spring Cloud Alibaba 2025.0.x
- AI: Spring AI 1.1.x, Alibaba Cloud Model Studio compatible API, Qdrant
- Data: MySQL 8, Redis, RabbitMQ, MinIO, Qdrant
- Deployment: Docker Compose on one ECS instance

## Layout

```text
backend/
  common/
  gateway-service/
  user-service/
  content-service/
  shop-service/
  inventory-service/
  order-service/
  payment-service/
  ingestion-service/
  assistant-service/
frontend/
  web/
  admin/
deploy/
  mysql/
  nginx/
  observability/
  operations/
```

## Quick Start

```powershell
npm install
npm run build:local
npm run setup:auth-keys
mvn -q -DskipTests package
docker compose up -d
```

只提供数据演示，非真实数据。

## AI 客服

登录用户启用后可以咨询站内功能、搜索作品与商品，并根据书架和最近阅读获得推荐。模型只能调用作品、商品和阅读摘要三个只读工具，不具备下单、支付、充值或自动修改购物车的能力。

本地启用阿里云百炼兼容接口：

```powershell
$env:AI_ENABLED = 'true'
$env:AI_API_KEY = '<本地环境变量中的 API Key>'
$env:AI_BASE_URL = '<百炼工作空间的 compatible-mode/v1 地址>'
docker compose up -d --build --wait
```

模型默认使用关闭深度思考的 `qwen-plus`，向量模型使用 `text-embedding-v4`。可通过 `AI_CHAT_MODEL`、`AI_ENABLE_THINKING` 和 `AI_EMBEDDING_MODEL` 覆盖。API Key 不得写入代码、Compose 文件或 Git；生产环境使用 [`docker-compose.ai.yml`](docker-compose.ai.yml) 的 Secret 覆盖方式。

## Core Regression Tests

先启动并等待 Docker Compose 服务健康：

```powershell
npm run setup:auth-keys
docker compose up -d
npm run test:core
```

`test:core` 会依次运行令牌安全、登录会话与订单超时策略测试、真实网关 API 流程和 Chrome 页面流程。也可以单独运行：

```powershell
npm run test:core:backend
npm run test:core:api
npm run test:core:web
```

可通过 `CORE_API_BASE_URL` 和 `CORE_WEB_BASE_URL` 指向其他测试环境。API 与页面测试会使用随机邮箱注册隔离账号。

## GitHub CI

`.github/workflows/core-flow.yml` 会在以下情况运行完整核心回归：

- 提交以 `master` 为目标分支的 Pull Request。
- 代码推送到 `master`。
- 在 GitHub Actions 页面手动触发。

CI 使用 Java 21、Node.js 20、Chromium 和 Docker Compose。失败时会保留 7 天的容器日志、Playwright 截图和 trace。

## Observability And Operations

生产与公开 Demo 环境可以叠加 `docker-compose.observability.yml`，启用 Prometheus 指标采集、告警规则和 Grafana 运维看板。后端响应会返回 `X-Trace-Id`；本地日志会显示该编号，生产日志使用包含 `traceId` 的结构化 JSON，方便串联一次请求经过的服务。

```powershell
$env:GRAFANA_ADMIN_PASSWORD = '<从 Secret 注入的独立密码>'
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --wait
```

- Prometheus：`http://127.0.0.1:9090`
- Grafana：`http://127.0.0.1:3000`

备份、恢复演练、部署冒烟测试和生产定时任务的完整说明见 [`deploy/operations/README.md`](deploy/operations/README.md)。生产与公开 Demo 的配置说明见 [`deploy/production/README.md`](deploy/production/README.md)。

## Authentication

- `user-service` 使用 RS256 私钥签发 15 分钟 Access Token。
- 网关和业务服务只挂载公钥，并分别校验签名、签发方、受众、密钥编号和时间声明。
- Refresh Token 存放在 HttpOnly Cookie 中，网页保持打开时在后台续期；关闭后连续 72 小时未使用则需要重新登录。
- `npm run setup:auth-keys` 生成的 PEM 文件只用于本地开发且不会进入 Git。生产环境必须从密钥管理服务或部署平台 Secret 注入独立密钥。
- HTTPS 部署必须设置 `AUTH_COOKIE_SECURE=true`，并将跨域来源限制为实际站点域名。

## Environment Profiles

- `local`：由 `docker-compose.yml` 显式启用，允许模拟支付、演示导入、测试账号和开发密码重置码。
- `test`：仅供自动测试使用，允许受测试覆盖的模拟组件。
- `prod`：拒绝与 `local/test` 混用，并在启动时校验数据库与消息队列密码、JWT 密钥位置、安全 Cookie 和精确 HTTPS CORS 来源。
- `demo`：沿用生产安全校验，开放明确标注的模拟支付，对登录、注册、密码找回和支付接口启用 Redis 分布式限流，并每六个月清理一次非管理员演示数据。

所有支付模式都会先由服务端通过 `/api/v1/payments/checkout-session` 校验订单归属、金额和状态。`npm run build:local` 构建本地 Demo 支付网页；`npm run build:demo` 构建可公开展示的 Demo 网页，并明确提示不会产生真实扣款。`npm run build` 默认生成生产网页，只保留跳转型支付路径。旧的 `mock-confirm` 仅供本地和自动测试兼容，公开 Demo 不注册该接口。

生产 Compose 配置使用 `docker-compose.production.yml` 覆盖本地默认值，所有敏感值都必须由部署环境提供。具体变量和启动方式见 `deploy/production/README.md`。真实支付与密码重置邮件提供商尚未接入前，相应生产能力保持关闭，不使用本地模拟器冒充正式服务。

个人作品集可以使用 `demo` Profile 公开展示模拟支付。它复用生产环境安全校验，并要求显式启用 Demo 支付与半年数据清理；启动方式见 `deploy/production/README.md`。Demo 环境不得接收真实资金。

首次工作流成功运行后，可在 GitHub 的 `master` Ruleset 中启用“必须通过状态检查”，选择 `Core flow regression`，并要求通过 Pull Request 合并。此后使用：

```text
功能分支 -> 推送远端 -> 创建 Pull Request -> Core flow regression 通过 -> 合并
```
