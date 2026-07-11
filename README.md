# Gray Anime Commerce

二次元综合电商平台 MVP：内容阅读、实体商城、VIP/积分、限量抢购、后台运营看板。

## Stack

- Frontend: React + TypeScript + Vite
- Backend: Java 17, Spring Boot 3.5.x, Spring Cloud 2025.0.x, Spring Cloud Alibaba 2025.0.x
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
docker compose up -d
npm run test:core
```

`test:core` 会依次运行订单超时策略测试、真实网关 API 流程和 Chrome 页面流程。也可以单独运行：

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

CI 使用 Java 17、Node.js 20、Chromium 和 Docker Compose。失败时会保留 7 天的容器日志、Playwright 截图和 trace。

首次工作流成功运行后，可在 GitHub 的 `master` Ruleset 中启用“必须通过状态检查”，选择 `Core flow regression`，并要求通过 Pull Request 合并。此后使用：

```text
功能分支 -> 推送远端 -> 创建 Pull Request -> Core flow regression 通过 -> 合并
```
