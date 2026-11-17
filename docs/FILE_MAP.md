# 目录说明

| 路径 | 用途 |
|---|---|
| [README.md](../README.md) | 本机部署、业务操作和集成验收入口 |
| [AGENTS.md](../AGENTS.md) | 项目约定及文档入口 |
| [compose.yaml](../compose.yaml)、[.env.example](../.env.example) | Compose 服务、持久卷与可选环境参数 |
| [backend/](../backend/) | Spring Boot 业务服务与镜像构建 |
| [backend/src/main/java/com/example/stock/](../backend/src/main/java/com/example/stock/) | API、库存事务、金额模型与 MyBatis 数据访问 |
| [backend/src/main/resources/](../backend/src/main/resources/) | 应用配置、SQL 映射和五张业务表定义 |
| [backend/src/test/](../backend/src/test/) | 后端业务计算测试 |
| [frontend/](../frontend/) | Vue 应用、Vite 构建和 Nginx 代理配置 |
| [frontend/src/](../frontend/src/) | 中文工作台、组件、样式和 HTTP 请求 |
| [tests/inventory-smoke.ps1](../tests/inventory-smoke.ps1) | 连接独立部署栈验证业务规则与持久性 |
| [deploy/](../deploy/) | 复用已有 OpenResty、MySQL 的独立服务器配置 |
| [docs/tasks/](tasks/) | 项目任务记录及[总索引](tasks/README.md) |
