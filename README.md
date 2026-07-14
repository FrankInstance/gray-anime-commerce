# Gray Anime Commerce

二次元综合电商平台 MVP：内容阅读、实体商城、VIP/积分、限量抢购、后台运营看板。

## Stack

- Frontend: React + TypeScript + Vite
- Backend: Java 21, Spring Boot 3.5.x, Spring Cloud 2025.0.x, Spring Cloud Alibaba 2025.0.x
- Data: MySQL 8, Redis, RabbitMQ, MinIO
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
frontend/
  web/
  admin/
deploy/
  mysql/
  nginx/
```

## Quick Start

```powershell
npm install
npm run build:local
npm run setup:auth-keys
mvn -q -DskipTests package
docker compose up -d
```

Development ports:

- Web: `5173`
- Admin: `5174`
- Gateway: `8080`
- RabbitMQ console: `15672`
- Nacos: `8848`
- MinIO console: `9001`

本地环境提供开放内容的演示导入，不实现未经授权的小说或漫画抓取。演示导入不会在生产 Profile 注册。

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

`npm run build:local` 构建可完成模拟支付的本地网页。`npm run build` 默认生成生产网页，支付动作会进入预留的真实支付会话接口，不会调用 `mock-confirm`。

生产 Compose 配置使用 `docker-compose.production.yml` 覆盖本地默认值，所有敏感值都必须由部署环境提供。具体变量和启动方式见 `deploy/production/README.md`。真实支付与密码重置邮件提供商尚未接入前，相应生产能力保持关闭，不使用本地模拟器冒充正式服务。

首次工作流成功运行后，可在 GitHub 的 `master` Ruleset 中启用“必须通过状态检查”，选择 `Core flow regression`，并要求通过 Pull Request 合并。此后使用：

```text
功能分支 -> 推送远端 -> 创建 Pull Request -> Core flow regression 通过 -> 合并
```
