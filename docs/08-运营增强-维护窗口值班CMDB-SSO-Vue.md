# 运营增强：维护窗口/静默、值班排班、CMDB 对接、SSO、Vue 工程化前端

> 在里程碑 2 基础上扩展。代码见 `monitor-integration/`、`frontend/`。

## 1. 维护窗口 / 静默

- 表 `maintenance_window`：scope_type(global/service/monitor) + scope_value + 时间窗。
- 命中**启用且生效中**的维护窗口时，告警**仍归档但跳过通知**（日志「命中维护窗口，静默通知」）。
- API：`GET/POST /api/v1/maintenance-windows`、`DELETE /api/v1/maintenance-windows/{id}`。
- 前端：「维护窗口」页签管理。

## 2. 值班排班（On-call）

- 表 `oncall_schedule`：name + service_name(空=全局) + 当班企业微信/邮件 + 时间窗。
- 通知接收人 = **服务负责人 ∪ 当前当班值班人**（按服务匹配或全局）。
- API：`GET/POST /api/v1/oncall/schedules`、`DELETE /api/v1/oncall/schedules/{id}`、`GET /api/v1/oncall/current?serviceName=`。
- 前端：「值班排班」页签 + 查当前当班。

## 3. CMDB 对接

- 从公司 CMDB 拉取「服务→负责人」映射，同步进 `service_owner`。
- API：`POST /api/v1/cmdb/sync`
  - `{"url":"https://cmdb/api/services","serviceField":"...","wecomField":"...","emailField":"..."}`（字段名可适配）
  - 或内联 `{"items":[{"serviceName":"...","wecomUserids":"...","emailList":"..."}]}`
- URL 拉取经 **SSRF 校验**（同 OpenAPI 导入）。各公司 CMDB 结构不同，可在 `CmdbService` 增加适配层；建议配合定时任务周期同步。
- 前端：「CMDB」页签。

## 4. SSO 集成

提供两种鉴权，二选一通过即放行管理 API：
- **API Key**：请求头 `X-Api-Key`（`API_KEY`）。
- **SSO 网关可信身份头**：配置 `SSO_TRUSTED_HEADER`（如 `X-Auth-User`），由**前置 SSO 网关**完成 OIDC/CAS 等登录后注入该头。
  - 安全前提：网关必须强制 SSO，并**剥离客户端伪造的同名头**；集成层只信任来自网关的请求（建议配合网络隔离/mTLS）。
- 如需在应用内直接做 OIDC 登录，可引入 `spring-boot-starter-oauth2-client/resource-server` 扩展（本期采用网关前置方案，零额外依赖、易落地）。

## 5. Vue 工程化前端

- 技术栈：**Vue 3 + Vite + Vue Router + axios**，源码在 `frontend/`。
- 构建集成：`monitor-integration` 的 Maven 通过 **frontend-maven-plugin** 在 `package` 阶段自动构建前端并打入 jar 的 `static/ui`（`mvn -Dskip.frontend=true package` 可跳过）。
  Docker 多阶段构建（context=仓库根）同样从源码构建。
- 访问：`http://<host>:8080/`（自动跳 `/ui/`）。SPA history 模式深链由 `SpaForwardController` 转发。
- 开发：`cd frontend && npm i && npm run dev`（Vite 代理 `/api`、`/health` 到 `:8080`）。
- 页签：概览(趋势)、告警(过滤/详情/Ack/响应时间曲线)、SLA(可用率趋势)、监控项、服务负责人、维护窗口、值班排班、导入纳管、CMDB、通知测试。顶部支持 X-Api-Key 与明暗主题。

## 6. 集成层自监控（避免"监控系统挂了没人知道"）

- **Actuator + Prometheus 指标**：`/actuator/health`、`/actuator/prometheus`（JVM/HTTP/Hikari 等），
  可被 Prometheus 抓取或在 HertzBeat 里把本服务也作为一个监控目标，实现对监控系统自身的反向监控。
- **Dead Man's Switch 心跳**：配置 `HEARTBEAT_URL`（如 Healthchecks.io ping 地址）+ `INTEGRATION_HEARTBEAT_INTERVAL_MS`，
  集成层定期上报心跳；外部看门狗在超时未收到时**独立**告警（不依赖本系统）。

## 7. CMDB 定时同步

- 配置 `cmdb.url` + `cmdb.scheduled-enabled=true` + `cmdb.sync-interval-ms`，周期性从 CMDB 同步服务负责人。
- 也可随时手动 `POST /api/v1/cmdb/sync`。

## 8. CI（GitHub Actions）

`.github/workflows/ci.yml`：push/PR 触发，后端 `mvn test`（跳过前端构建以加速）+ 打包，前端 `npm ci && npm run build`。

## 9. 安全/可靠性加固（代码审计整改）

- **依赖升级**：Spring Boot 升级至 3.3.13、springdoc 2.6.0；CI 增加 **Trivy** 依赖/配置漏洞扫描；前端构建改用 `npm ci`。
- **actuator 加固**：`/actuator/health` 放行（探针），`/actuator/prometheus`、`/metrics` 等需 `X-Api-Key`。
- **安全严格模式**：`SECURITY_STRICT=true` 时，缺少 API_KEY/SSO 头或 webhook-token 为默认/空 → 拒绝启动。
- **fingerprint 稳定化**：优先用 HertzBeat 原生 `fingerprint`，否则用稳定标签子集(defineid/alertname/instance/instancename)计算，避免易变指标值标签导致 firing/resolved 关联失败。
- **并发去重**：按 fingerprint 条带锁串行处理，避免并发产生重复 firing。
- **多副本就绪（ShedLock）**：升级扫描、CMDB 同步用分布式锁，多实例只有一个执行（表 `shedlock`，Flyway V5）。
- **事务取舍**：通知/导入涉及外部 HTTP 调用，刻意不包裹长事务（避免占用连接）；落库失败处用补偿（如 Provision 回滚 HertzBeat 监控）。

## 10. 相关环境变量

| 变量 | 说明 |
| --- | --- |
| `API_KEY` | 管理 API X-Api-Key |
| `SSO_TRUSTED_HEADER` | SSO 网关可信身份头名（如 X-Auth-User）|
| `NOTIFY_MODE` / `ESCALATE_*` | 见 docs/07 |
| `SECURITY_STRICT` | 安全严格模式（true 时弱鉴权拒绝启动）|
| `HEARTBEAT_URL` / `HEARTBEAT_INTERVAL_MS` | Dead Man's Switch 心跳 |
| `cmdb.scheduled-enabled` / `cmdb.sync-interval-ms` | CMDB 定时同步 |
