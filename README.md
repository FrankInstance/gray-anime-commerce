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
