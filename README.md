# ZhidongJiankongFuwu（自动化监控服务）

面向公司 SaaS、平台级软件及背后各类基础服务的 **API 接口级监控告警系统**。
当某个 API 接口不可访问 / 返回错误时，第一时间通过**邮件、企业微信**通知运维，并对所有监控对象、探测结果、告警记录进行**数据库化管理**。

> 当前阶段：**方案设计（暂不开发）**。

## 设计文档导航

| 文档 | 内容 |
| --- | --- |
| [01-总体方案设计](docs/01-总体方案设计.md) | 背景目标、总体架构、核心模块、防误报策略 |
| [02-通知渠道设计](docs/02-通知渠道设计.md) | 邮件 & 企业微信（群机器人 / 应用消息）详细设计与示例 |
| [03-数据库设计](docs/03-数据库设计.md) | 完整表结构 DDL、ER 图、数据保留策略 |
| [04-技术选型与部署](docs/04-技术选型与部署.md) | 技术栈、部署架构、安全、自监控、实施路线图 |
| [05-API接口设计](docs/05-API接口设计.md) | 监控平台对外提供的 REST API 设计 |
| [06-MVP开发提示词](docs/06-MVP开发提示词.md) | 基于开源 Apache HertzBeat 二次开发（HertzBeat 引擎 + Java 集成层），分阶段喂给 AI 编码代理的 MVP 开发提示词 |
| [07-里程碑2](docs/07-里程碑2-多探测点告警升级SLA.md) | 通知兜底切换、告警升级、SLA 大盘、多探测点（多采集器）设计与用法 |

## 实现进度（基于 Apache HertzBeat 二次开发）

| 模块 | 路径 | 状态 |
| --- | --- | --- |
| HertzBeat 部署（引擎 + MySQL + VictoriaMetrics）| `deploy/hertzbeat/` | ✅ 阶段0 完成，已实测启动 |
| Java 集成层骨架（Spring Boot）| `monitor-integration/` | ✅ 阶段3 脚手架完成（HertzBeat 客户端、健康检查、Flyway 建表），已实测连通 |
| OpenAPI 自动导入 / 批量纳管 | `monitor-integration/` | ✅ 阶段4 完成，已实测（OpenAPI 解析、dryRun 预览、幂等创建/更新到 HertzBeat）|
| 告警归档 + 企业微信应用消息(精准@人) + 邮件 | `monitor-integration/` | ✅ 阶段5 完成，已实测（Webhook 归档、firing/recovered 状态机、通知路由与回执、查询/统计 API）|
| 一体化部署联调 + 单测 | `docker-compose.yml` / `deploy/README.md` / `monitor-integration/src/test` | ✅ 阶段6 完成（一体化 compose、单元测试、端到端自检清单）|
| 安全加固（API Key/Webhook/SSRF）+ 导入自动建告警规则与转发 | `monitor-integration/` | ✅ 已完成并实测 |
| 里程碑2：通知兜底切换、告警升级、SLA 大盘、多探测点 | `monitor-integration/` + `docs/07` | ✅ 已完成并实测（多探测点需部署额外采集器）|

> MVP 已确定包含：**OpenAPI 自动导入、VictoriaMetrics 时序库、企业微信应用消息精准@人**。
> 详见 `docs/06-MVP开发提示词.md`。一体化部署与端到端自检见 `deploy/README.md`，一条命令 `docker compose up -d --build` 起全栈。
> 本地实测：HertzBeat 启动、API 登录、MySQL 自动建表、OpenAPI 批量纳管、告警 Webhook 归档与通知路由、查询统计 API 全链路通过。

## 核心能力概览

- 接口级主动拨测：可用性、状态码、响应时间、响应体断言。
- 实时告警：状态机 + 连续失败判定，降低误报。
- 多渠道通知：邮件、企业微信，可扩展短信/钉钉/电话。
- 告警治理：去重、收敛、抑制、静默、升级、恢复通知。
- 数据管理：监控项、探测结果、告警记录、通知策略全部入库，配套管理后台。
