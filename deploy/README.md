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
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | root123456 |
| `HERTZBEAT_USERNAME` / `HERTZBEAT_PASSWORD` | HertzBeat 账号 | admin / hertzbeat |
| `WEBHOOK_TOKEN` | 告警 Webhook 校验密钥 | change-me |
| `DETAIL_BASE_URL` | 通知中详情链接基础地址 | http://localhost:1157 |
| `WECOM_CORPID` / `WECOM_AGENTID` / `WECOM_SECRET` / `WECOM_DEFAULT_USERIDS` | 企业微信自建应用（精准@人） | 空（不配则跳过企业微信） |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | 邮件 SMTP | 空（不配则跳过邮件） |

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

3. **在 HertzBeat 配置告警 Webhook 通知**（控制台 → 告警通知 → 新增接收人，类型 Webhook）：
   - URL：`http://monitor-integration:8080/api/v1/webhook/hertzbeat`（容器内）或 `http://<宿主IP>:8080/...`
   - 在 HertzBeat 通知策略里把告警分发到该 Webhook。
   - 在 Webhook 请求头加 `X-Webhook-Token: <WEBHOOK_TOKEN>`（若该版本支持自定义 header；否则将 `integration.webhook-token` 置空以关闭校验）。

4. **配置告警阈值规则**：对监控项设置「响应码异常 / 响应时间过高 / JSON 业务码非 0」等阈值告警（参考 `deploy/hertzbeat/`）。

## 五、自检清单（验证 MVP 完整链路）

- [ ] 全栈启动：`docker compose ps` 全部 healthy/up
- [ ] HertzBeat 控制台可登录，VictoriaMetrics `/health` = OK
- [ ] 集成层 `/health` 返回 UP；`/api/v1/hertzbeat/monitors` 能拉到 HertzBeat 监控
- [ ] OpenAPI 导入：dryRun 预览正确；正式导入后 HertzBeat 出现对应监控、`/api/v1/monitors` 可查映射
- [ ] 含路径参数的接口在预览中标记 `needsReview`
- [ ] 接口异常 → HertzBeat 触发告警 → 集成层 `/api/v1/webhook/hertzbeat` 收到 → `/api/v1/alerts` 出现 firing
- [ ] 对应服务负责人在企业微信被精准@到 + 收到邮件（需配置 WeCom/SMTP）
- [ ] 接口恢复 → 告警状态变 `recovered`，`durationSec` 为正
- [ ] `/api/v1/alerts/{id}` 含 `notifyLogs` 通知回执；`/api/v1/stats/overview` 统计正确
- [ ] 关闭集成层时，HertzBeat 原生通知（若已配置）仍可兜底发出

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
