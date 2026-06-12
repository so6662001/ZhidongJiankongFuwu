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

### 阶段 5：告警归档 + 企业微信应用消息(精准@人) + 邮件 + 查询/统计

- **告警 Webhook**：`POST /api/v1/webhook/hertzbeat`（请求头 `X-Webhook-Token` 校验）。解析 HertzBeat
  `GroupAlert`（Alertmanager 风格 payload），按 `instancename` 关联本地 `monitor_ref` 补充服务/URL/等级，
  按 fingerprint 维护 firing/recovered 状态机与持续时长，异常也返回 200 避免重推。
- **通知网关**：按 `service_owner`（服务→负责人映射）路由，发企业微信**应用消息(textcard 精准@人)** + 邮件，
  access_token 经 Redis 缓存（失败回退内存），邮件失败指数退避重试，全部写 `notify_log` 回执。
- **查询/统计**：`GET /api/v1/alerts`（分页/过滤）、`GET /api/v1/alerts/{id}`（含通知回执）、
  `GET /api/v1/stats/overview`（当前 firing/今日告警/按服务分布）。
- **服务负责人映射**：`GET/POST /api/v1/service-owners`、`DELETE /api/v1/service-owners/{id}`。

```bash
# 在 HertzBeat 配置 Webhook 通知，URL 指向：
#   http://<integration-host>:8080/api/v1/webhook/hertzbeat
#   并在请求头加 X-Webhook-Token: <WEBHOOK_TOKEN>
# 配置服务负责人（精准@人 + 邮件）
curl -X POST http://localhost:8080/api/v1/service-owners -H 'Content-Type: application/json' \
  -d '{"serviceName":"order-service","wecomUserids":"zhangsan,lisi","emailList":"ops@company.com"}'
```

> 后续阶段（6）：一体化 docker-compose 联调、单元测试、自检清单。

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
