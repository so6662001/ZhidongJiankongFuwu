# 一体化部署与端到端自检

一条命令拉起全栈：**HertzBeat + MySQL + VictoriaMetrics + Redis + monitor-integration（Java 集成层）**。

## 一、前置

- 安装 Docker 与 Docker Compose v2。
- 下载 HertzBeat 的 MySQL 驱动（官方镜像未内置，必做）：

```bash
bash deploy/hertzbeat/ext-lib/download.sh
```

## 二、启动

```bash
# 在仓库根目录
docker compose up -d --build

# 查看状态
docker compose ps
docker compose logs -f monitor-integration
```

可选环境变量（在 `docker compose up` 前 export 或写入 `.env`）：

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码（生产必改） | root123456 |
| `HERTZBEAT_USERNAME` / `HERTZBEAT_PASSWORD` | HertzBeat 账号 | admin / hertzbeat |
| `HZB_JWT_SECRET` | **HertzBeat JWT 密钥，生产必改**（默认是官方公开值，可被伪造 JWT） | 占位默认值 |
| `API_KEY` | **集成层管理 API 鉴权 Key（请求头 X-Api-Key），生产必配**；为空则管理 API 无鉴权 | 空 |
| `WEBHOOK_TOKEN` | 告警 Webhook 校验密钥（HertzBeat 经 `Authorization: Bearer` 携带；fail-closed，空则拒收） | change-me |
| `SELF_WEBHOOK_URL` | 本服务 webhook 可达地址，**配置后导入时自动在 HertzBeat 建 webhook 接收人+全量转发策略** | 容器内服务名地址 |
| `OPENAPI_ALLOWED_HOSTS` / `OPENAPI_ALLOW_PRIVATE` | OpenAPI 拉取 host 白名单 / 是否允许私网（防 SSRF） | 空 / false |
| `DETAIL_BASE_URL` | 通知中详情链接基础地址 | http://localhost:1157 |
| `WECOM_CORPID` / `WECOM_AGENTID` / `WECOM_SECRET` / `WECOM_DEFAULT_USERIDS` | 企业微信自建应用（精准@人） | 空（不配则跳过企业微信） |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | 邮件 SMTP | 空（不配则跳过邮件） |

> **安全要点（生产部署务必处理）**：① 配置 `API_KEY`（否则管理 API 无鉴权）；② 修改 `WEBHOOK_TOKEN`；
> ③ 修改 `HZB_JWT_SECRET` 与 HertzBeat 默认口令；④ 修改 `MYSQL_ROOT_PASSWORD`；
> ⑤ OpenAPI 拉取默认拦截环回/链路本地/私网地址（防 SSRF），如需拉内网文档配 `OPENAPI_ALLOWED_HOSTS` 或 `OPENAPI_ALLOW_PRIVATE=true`。

## 三、访问

| 项 | 地址 |
| --- | --- |
| HertzBeat 控制台 | http://localhost:1157 （admin/hertzbeat，首登改密） |
| 集成层 Swagger | http://localhost:8080/swagger-ui.html |
| 集成层健康检查 | http://localhost:8080/health |
| VictoriaMetrics | http://localhost:8428 |

## 四、端到端操作流程

1. **批量纳管接口**（OpenAPI 自动导入，先预览后写入）：

```bash
# dryRun 预览
curl -X POST http://localhost:8080/api/v1/provision/import-openapi \
  -H 'Content-Type: application/json' \
  -d '{"openapiContent":"<OpenAPI JSON/YAML>","serviceName":"order-service","severity":"P1","dryRun":true,"includeMethods":["GET"]}'

# 正式导入
curl -X POST http://localhost:8080/api/v1/provision/import-openapi \
  -H 'Content-Type: application/json' \
  -d '{"openapiUrl":"https://your-svc/v3/api-docs","serviceName":"order-service","dryRun":false}'
```

2. **配置服务负责人**（企业微信精准@人 + 邮件）：

```bash
curl -X POST http://localhost:8080/api/v1/service-owners -H 'Content-Type: application/json' \
  -d '{"serviceName":"order-service","wecomUserids":"zhangsan,lisi","emailList":"ops@company.com"}'
```

3. **告警规则与转发（已自动化，无需手工配置）**：
   - 调用导入接口（非 dryRun）时，集成层会自动在 HertzBeat 创建 3 条默认告警规则（覆盖你的核心诉求）：
     - `auto-api-unavailable`：接口**无法访问**（超时/连接失败）→ critical
     - `auto-api-status-error`：接口**返回错误码**(>=400) → critical
     - `auto-api-slow-response`：响应过慢(>3s) → warning
   - 若配置了 `SELF_WEBHOOK_URL`，还会自动创建 webhook 接收人（`Authorization: Bearer <WEBHOOK_TOKEN>`）与全量转发策略，
     把告警回推到集成层 → 归档 + 企业微信精准@人/邮件通知。
   - 如需自定义阈值（如不同接口不同响应时间阈值），可在 HertzBeat 控制台调整这些规则。

## 五、自检清单（验证 MVP 完整链路）

- [ ] 全栈启动：`docker compose ps` 全部 healthy/up
- [ ] HertzBeat 控制台可登录，VictoriaMetrics `/health` = OK
- [ ] 集成层 `/health` 返回 UP；带 `X-Api-Key` 访问 `/api/v1/hertzbeat/monitors` 能拉到监控；不带 key → 401
- [ ] OpenAPI 导入（带 `X-Api-Key`）：dryRun 预览正确；正式导入后 HertzBeat 出现监控、`/api/v1/monitors` 可查映射
- [ ] 含路径参数的接口在预览中标记 `needsReview`
- [ ] 正式导入后 HertzBeat 自动出现 3 条 `auto-api-*` 告警规则 + `auto-integration-webhook` 接收人 + 转发策略
- [ ] 接口异常/不可访问 → HertzBeat 触发告警 → 回推集成层 → `/api/v1/alerts` 出现 firing
- [ ] 对应服务负责人在企业微信被精准@到 + 收到邮件（需配置 WeCom/SMTP）
- [ ] 接口恢复 → 告警状态变 `recovered`，`durationSec` 为正
- [ ] `/api/v1/alerts/{id}` 含 `notifyLogs` 通知回执；`/api/v1/stats/overview` 统计正确
- [ ] 安全：未配 `API_KEY` 启动日志有安全告警；错误/缺失 token 的 Webhook → 401；SSRF 拉取环回/内网被拦截

## 六、单元测试

```bash
cd monitor-integration && mvn test
```

覆盖：OpenAPI 解析与导入预览（`ProvisionServiceTest`）、通知文案渲染（`TemplateRendererTest`）、
告警 firing/recovered 状态机（`AlertServiceTest`）。

## 七、受限环境（无 Docker bridge 网络）说明

部分沙箱环境 Docker 的 bridge/NAT 不可用，无法用默认网络跑多容器编排。可改用 host 网络逐个启动各服务，
并把配置中的服务名（`mysql`/`victoria-metrics`/`redis`/`hertzbeat`）替换为 `127.0.0.1`。本项目各组件均已用该方式实测：
HertzBeat 启动、API 登录、MySQL 自动建表、OpenAPI 批量纳管、告警 Webhook 归档与通知路由、查询统计 API 全链路通过。
