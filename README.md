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
npm run build
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

The project intentionally uses demo/open-content ingestion and does not implement unauthorized novel or manga scraping.

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

首次工作流成功运行后，可在 GitHub 的 `master` Ruleset 中启用“必须通过状态检查”，选择 `Core flow regression`，并要求通过 Pull Request 合并。此后使用：

```text
功能分支 -> 推送远端 -> 创建 Pull Request -> Core flow regression 通过 -> 合并
```
