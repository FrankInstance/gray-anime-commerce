# Gray Project Context

更新时间：2026-07-15

## 使用约定

- 每当对话上下文被压缩或摘要替换时，把继续工作所需的重要内容整理到本文件。
- 中文文本、SQL 种子数据和页面文案必须保持 UTF-8；终端出现疑似乱码时必须继续核对文件、接口和运行数据。
- 项目已进入收尾阶段，认证、订单、支付、库存和用户资产默认按正式项目标准实现并覆盖失败及越权路径。

## 当前状态

- 技术栈：React、TypeScript、Vite、Java 21、Spring Boot 3.5.x、Spring Cloud 2025.0.x、Spring Cloud Alibaba、MySQL、Redis、RabbitMQ、MinIO、Nacos。
- 部署：单机 Docker Compose；`local`、`test`、`prod`、`demo` Profile 已分离。
- 用户端核心流程已完成：注册与登录、阅读进度、章节积分兑换、充值与 Demo 支付、VIP、购物车、限购、订单和个人中心。
- 认证已使用 RS256 Access Token 与 HttpOnly Refresh Token；网页保持打开时可续期，关闭后连续 72 小时未使用需要重新登录。
- 公开作品集环境使用 `demo` Profile，沿用生产安全校验，只开放明确标注且不产生真实扣款的 Demo 支付；非管理员演示数据每六个月清理一次。
- GitHub Actions 使用 Java 21、Node.js 20、Chromium 和 Docker Compose 执行核心回归。

## 当前工作分支

- 分支：`codex/production-observability`。
- 目标：补齐生产可观测性、MySQL 备份恢复演练和部署后 Demo 冒烟测试。
- 当前改动尚未提交或合并。

## 本轮已实现

- 网关统一规范化、返回并记录 `X-Trace-Id`；所有服务将 Trace ID 放入 MDC。
- 后端服务开放内部 `/actuator/prometheus`，并添加统一 `application` 指标标签和 HTTP 延迟直方图。
- 生产日志使用 Logstash JSON；本地日志级别前缀显示 Trace ID。
- 新增可选 `docker-compose.observability.yml`，包含 Prometheus、Grafana、运维看板和告警规则。
- 新增一致性 MySQL 备份脚本、SHA-256 校验、隔离数据库恢复验证及 systemd 定时任务模板。
- 新增公开 Demo 部署冒烟脚本，覆盖注册、个人信息、Refresh Token 轮换、退出登录、重新登录、积分订单、Demo 支付和余额确认。
- CI 增加运维资产校验；运维配置测试已通过，common/gateway 单元测试已通过。

## 本轮验证结果

- 本地、生产、Demo 与监控 Compose 组合均可解析；Prometheus `promtool` 确认配置和 5 条告警规则有效。
- 所有后端与中间件容器健康；Prometheus 8/8 targets 为 up，Grafana 健康且看板 8 条 PromQL 均可执行，当前无触发告警。
- Trace ID 真实请求验证通过：响应只返回一个安全编号，网关日志可按相同编号检索。
- MySQL 一致性备份、SHA-256、逐表 `CHECK TABLE`、22 张表结构对比、核心表检查和临时库自动清理均通过；缺失校验和会被拒绝。
- Demo 部署冒烟通过：注册、Refresh Token 轮换、退出重登、积分订单、Demo 支付确认和余额到账均正常。
- 核心回归通过：Java 后端测试、11 条网关 API 流程、4 条 Playwright 页面流程。
- 40 个以上变更文本文件按严格 UTF-8 解码且未发现指定 mojibake 特征；`git diff --check` 通过。
- Prometheus 与 Grafana 使用只读根文件系统、`cap_drop=ALL`、`no-new-privileges`；Grafana 后台插件安装已关闭，当前全栈日志无 ERROR 或异常堆栈。

## 常用地址

- 用户端：http://127.0.0.1/
- 网关/API：http://127.0.0.1:8080
- Prometheus：http://127.0.0.1:9090
- Grafana：http://127.0.0.1:3000
- Nacos：http://127.0.0.1:8848/nacos

## 关键文档

- 根目录 `README.md`：项目启动、认证、环境和 CI 概览。
- `deploy/production/README.md`：生产与公开 Demo 部署要求。
- `deploy/operations/README.md`：监控、备份恢复和部署冒烟操作手册。
- `AGENTS.md`：产品文案、编码安全和正式项目标准。
