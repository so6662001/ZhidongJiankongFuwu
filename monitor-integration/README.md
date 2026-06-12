# monitor-integration（Java 集成层）

HertzBeat 集成增强层（Spring Boot 3 + Java 17）。已实现 **阶段 3 脚手架 + 阶段 4 批量纳管**：

- 工程骨架、统一返回体 `Result<T>`、全局异常处理、Swagger。
- `/health` 健康检查。
- Flyway 迁移（`monitor_ref`、`service_owner`、`alert`、`notify_log` 等表）。
- **HertzBeat REST API 客户端**：登录鉴权 + 监控增删改查，token 失效自动重登；更新时自动携带既有 param id（规避唯一键冲突）。
- `/api/v1/hertzbeat/monitors` 透传接口（验证与 HertzBeat 连通）。
- **OpenAPI 自动导入**：`POST /api/v1/provision/import-openapi`（解析 OpenAPI v3/Swagger v2，dryRun 预览，路径参数标记需人工确认，幂等创建/更新到 HertzBeat）。
- **清单批量导入**：`POST /api/v1/provision/import`（JSON 数组）。
- **本地监控项管理**：`GET /api/v1/monitors`（分页/过滤）、`PUT /api/v1/monitors/{id}`（业务属性）。

### 阶段 4 接口示例

```bash
# OpenAPI 导入（默认 dryRun=true 仅预览）
curl -X POST http://localhost:8080/api/v1/provision/import-openapi \
  -H 'Content-Type: application/json' \
  -d '{"openapiContent":"<OpenAPI JSON/YAML 文本>","serviceName":"order-service","severity":"P1","dryRun":false,"includeMethods":["GET"]}'

# 也可传 URL 由集成层拉取
# {"openapiUrl":"https://svc/v3/api-docs","dryRun":true}
```

> 后续阶段（5）：告警 Webhook 归档、企业微信应用消息(精准@人)+邮件通知、查询/报表 API。

## 本地运行

前置：已按 `deploy/hertzbeat` 启动 HertzBeat + MySQL + VictoriaMetrics，并有可用 Redis。

```bash
mvn -DskipTests package

export DB_URL='jdbc:mysql://127.0.0.1:3306/monitor_integration?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true'
export DB_USER=root DB_PASSWORD=root123456
export REDIS_HOST=127.0.0.1
export HERTZBEAT_BASE_URL=http://127.0.0.1:1157 HERTZBEAT_USERNAME=admin HERTZBEAT_PASSWORD=hertzbeat

java -jar target/monitor-integration.jar
```

## 环境变量

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | 集成层 MySQL（库 `monitor_integration`，自动创建） | localhost |
| `REDIS_HOST` / `REDIS_PORT` | 缓存企业微信 access_token | localhost:6379 |
| `HERTZBEAT_BASE_URL` / `HERTZBEAT_USERNAME` / `HERTZBEAT_PASSWORD` | HertzBeat 连接 | localhost:1157 / admin / hertzbeat |
| `WECOM_CORPID` / `WECOM_AGENTID` / `WECOM_SECRET` / `WECOM_DEFAULT_USERIDS` | 企业微信自建应用（精准@人） | 空 |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | 邮件 SMTP | 空 |
| `WEBHOOK_TOKEN` | HertzBeat 告警 Webhook 校验密钥 | change-me |

## 验证

```bash
curl http://localhost:8080/health
# {"code":0,"message":"ok","data":{"status":"UP",...}}

curl http://localhost:8080/api/v1/hertzbeat/monitors
# 集成层登录 HertzBeat 并返回其监控列表（验证连通）

# 接口文档
open http://localhost:8080/swagger-ui.html
```
