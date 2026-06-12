# 监控平台 REST API 设计

> 配套文档：`01-总体方案设计.md`
> 监控平台自身对外提供的管理 API（供 Web 后台 / 第三方集成调用）。
> 统一前缀：`/api/v1`，认证：Bearer Token（登录获取），返回统一 JSON 结构。

## 统一响应格式

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

`code = 0` 成功，非 0 为业务错误码。分页统一返回 `{ "list": [], "total": 0, "page": 1, "size": 20 }`。

---

## 1. 服务管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/services` | 服务列表（分页、按类型/关键字过滤）|
| POST | `/api/v1/services` | 新建服务 |
| GET | `/api/v1/services/{id}` | 服务详情 |
| PUT | `/api/v1/services/{id}` | 更新服务 |
| DELETE | `/api/v1/services/{id}` | 删除服务 |

## 2. 监控项管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/monitors` | 监控项列表（按服务/环境/状态过滤）|
| POST | `/api/v1/monitors` | 新建监控项 |
| GET | `/api/v1/monitors/{id}` | 详情（含断言规则）|
| PUT | `/api/v1/monitors/{id}` | 更新 |
| DELETE | `/api/v1/monitors/{id}` | 删除 |
| POST | `/api/v1/monitors/{id}/enable` | 启用 |
| POST | `/api/v1/monitors/{id}/disable` | 停用 |
| POST | `/api/v1/monitors/{id}/test` | 立即试探一次（返回实时结果）|
| POST | `/api/v1/monitors/import` | 批量导入（JSON/OpenAPI）|

**新建监控项请求示例**：

```json
{
  "service_id": 10,
  "name": "支付下单接口",
  "env": "prod",
  "url": "https://api.company.com/pay/order",
  "method": "POST",
  "headers": {"Content-Type": "application/json"},
  "body": "{\"probe\":true}",
  "auth_type": "bearer",
  "timeout_ms": 5000,
  "interval_sec": 30,
  "retry_times": 2,
  "probe_regions": ["cn-east", "cn-north"],
  "alert_rule_id": 3,
  "assertions": [
    {"source": "status_code", "operator": "eq", "expected": "200"},
    {"source": "body_json", "expression": "$.code", "operator": "eq", "expected": "0"},
    {"source": "latency", "operator": "lt", "expected": "800"}
  ]
}
```

## 3. 探测结果与状态

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/monitors/{id}/results` | 探测明细（时间范围、分页）|
| GET | `/api/v1/monitors/{id}/uptime` | 可用率/SLA（按天/小时聚合）|
| GET | `/api/v1/monitors/{id}/latency` | 响应时间趋势（P50/P95/P99）|
| GET | `/api/v1/dashboard/overview` | 总览大盘（健康/故障数、故障列表）|

## 4. 告警管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/alerts` | 告警列表（状态/等级/服务过滤）|
| GET | `/api/v1/alerts/{id}` | 告警详情（含通知记录）|
| POST | `/api/v1/alerts/{id}/ack` | 认领/确认告警 |
| POST | `/api/v1/alerts/{id}/remark` | 添加处理备注 |

## 5. 告警规则

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST | `/api/v1/alert-rules` | 规则列表/新建 |
| PUT/DELETE | `/api/v1/alert-rules/{id}` | 更新/删除 |

## 6. 通知渠道与策略

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST | `/api/v1/channels` | 渠道列表/新建（email/wecom_robot/wecom_app）|
| PUT/DELETE | `/api/v1/channels/{id}` | 更新/删除 |
| POST | `/api/v1/channels/{id}/test` | 发送测试通知，验证渠道连通 |
| GET/POST | `/api/v1/receivers` | 接收人 |
| GET/POST | `/api/v1/receiver-groups` | 接收组 |
| GET/POST | `/api/v1/notify-policies` | 通知路由策略 |

## 7. 维护窗口/静默

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET/POST | `/api/v1/maintenance-windows` | 列表/新建 |
| DELETE | `/api/v1/maintenance-windows/{id}` | 取消 |

## 8. 内部接口（系统组件间）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/internal/probe/report` | Worker 上报探测结果 |
| GET | `/internal/scheduler/tasks` | 拉取待探测任务（或经 MQ）|
| POST | `/internal/health/heartbeat` | 组件心跳上报 |

---

## 9. Webhook（对外集成 / 给 Alertmanager 转发用）

若采用「Prometheus Alertmanager + 企业微信适配器」过渡方案，提供一个适配 Webhook：

```text
POST /api/v1/webhook/alertmanager
```

接收 Alertmanager 标准告警 JSON，转换为内部告警事件并走通知网关（邮件 + 企业微信），实现与现有 Prometheus 体系的无缝衔接。
