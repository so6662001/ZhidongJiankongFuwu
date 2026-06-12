# MVP 开发提示词（AI 编码代理用）

> 配套文档：`01-总体方案设计.md` ~ `05-API接口设计.md`
> 用途：本文件是一组**可直接喂给 AI 编码代理**（如 Cursor / Claude / Copilot）的结构化提示词，用于实现 API 监控告警系统的 **MVP（里程碑 1）**。
> 技术栈：**Java 17 + Spring Boot 3 + 全开源组件**。
> 使用方式：按「阶段 0 → 阶段 6」顺序，将每个阶段的提示词依次发给 AI 代理；每阶段完成、自测通过后再进行下一阶段。

---

## 0. MVP 范围界定（先对齐边界）

**MVP 要做的（In Scope）**：
- 监控项（Monitor）增删改查 + 启停 + 立即试探。
- 定时调度，按各监控项的间隔发起 HTTP(S) 探测。
- 探测判定：连接失败 / 超时 / 状态码不符 / 响应时间超阈值 / 响应体断言（JSONPath + 状态码 + 耗时）。
- 状态机 + 连续失败计数，产生告警事件与恢复事件。
- 通知：**邮件（SMTP）+ 企业微信群机器人（Webhook）**。
- 探测结果与告警记录入库；通知回执入库。
- Swagger UI 作为临时管理界面（不单独做前端）。

**MVP 暂不做的（Out of Scope，留给后续里程碑）**：
- Web 管理前端、RBAC 权限、登录鉴权（MVP 可先内网部署 + 简单 token）。
- 多探测点 / 多地域、告警升级、值班排班、抑制。
- 企业微信「自建应用消息」（MVP 先用群机器人，最简单）。
- 时序库、SLA 报表大盘（MVP 探测结果先入 MySQL）。
- 维护窗口/静默（MVP 可用监控项「停用」临时替代）。

---

## 1. 技术栈与全局约束（每个阶段都适用，作为公共上下文）

```text
【公共上下文 / 请在每个阶段开头附带】

我们在开发一个「API 接口级监控告警系统」的 MVP，技术栈与约束如下，请严格遵守：

- 语言/运行时：Java 17。
- 框架：Spring Boot 3.2.x，构建工具 Maven。
- 持久层：MyBatis-Plus 3.5.x + MySQL 8.0；数据库迁移用 Flyway。
- 定时调度：Spring @Scheduled（MVP 单实例即可），并预留 ShedLock 接口以便后续多实例分布式调度。
- HTTP 探测客户端：OkHttp 4.x（支持连接/读写超时、拿到各阶段耗时）。
- 邮件：spring-boot-starter-mail（JavaMail）。
- 企业微信：群机器人 Webhook，直接用 OkHttp POST JSON。
- JSON：Jackson；JSONPath 断言用 com.jayway.jsonpath:json-path。
- 工具库：Hutool（按需）、Lombok。
- API 文档：springdoc-openapi（Swagger UI），路径 /swagger-ui.html。
- 配置：application.yml，敏感配置（SMTP 密码、webhook key）支持环境变量注入，不要硬编码明文。
- 全部使用开源组件，禁止引入收费/闭源依赖。
- 代码规范：分层清晰（controller / service / mapper / entity / dto / config / probe / notify / alert）；
  统一返回体 Result<T>{code,message,data}；统一异常处理；关键路径打日志。
- 注释克制：只在非显而易见处写注释，不要逐行解释代码。
- 每个阶段产出后，给出：改动文件清单、如何本地运行验证的步骤。
```

---

## 2. 阶段 0：项目脚手架

```text
【阶段0 提示词】（附带第1节公共上下文）

任务：初始化 Spring Boot 3 + Maven 项目骨架。

要求：
1. Maven 项目，groupId=com.company.monitor，artifactId=api-monitor，Java 17。
2. 引入依赖：spring-boot-starter-web、spring-boot-starter-validation、
   spring-boot-starter-mail、mybatis-plus-spring-boot3-starter、mysql-connector-j、
   flyway-core + flyway-mysql、com.squareup.okhttp3:okhttp、com.jayway.jsonpath:json-path、
   org.projectlombok:lombok、cn.hutool:hutool-all、
   org.springdoc:springdoc-openapi-starter-webmvc-ui。
3. application.yml：配置数据源（从环境变量读 DB_URL/DB_USER/DB_PASS，给本地默认值）、
   MyBatis-Plus、Flyway 开启、邮件占位配置、自定义配置段 monitor.*（如线程池大小）。
4. 建立包结构：controller / service(impl) / mapper / entity / dto / common(Result、异常) / 
   config / probe / notify / alert / scheduler。
5. 实现统一返回体 Result<T> 与全局异常处理 @RestControllerAdvice。
6. 一个 /health 健康检查接口，启动后能访问 Swagger UI。
7. 提供 README 片段：本地启动步骤、所需环境变量。

产出：可成功 mvn compile / 启动的最小工程。
```

---

## 3. 阶段 1：数据库与核心实体（MVP 子集）

```text
【阶段1 提示词】（附带公共上下文）

任务：用 Flyway 创建 MVP 所需数据表，并生成对应 MyBatis-Plus 实体与 Mapper。

MVP 表（参考 docs/03-数据库设计.md，取子集）：
1. service（服务/业务线）：id,name,code(唯一),type,enabled,created_at,updated_at。
2. monitor（监控项）：id,service_id,name,env,url,method,headers(JSON),body,auth_type,
   timeout_ms(默认5000),interval_sec(默认60),retry_times(默认2),verify_tls,
   alert_fail_threshold(默认3),alert_recover_threshold(默认2),severity(默认P2),
   renotify_interval_sec(默认600),status(默认UP),enabled,created_at,updated_at。
   （MVP 把告警规则字段内联到 monitor，简化模型）
3. monitor_assertion（断言）：id,monitor_id,source(status_code/latency/body_json),
   expression,operator(eq/neq/in/lt/gt/contains),expected,enabled。
4. probe_result（探测结果）：id,monitor_id,success,http_code,latency_ms,error_type,
   error_msg,resp_snippet,probed_at。索引(monitor_id,probed_at)。
5. alert（告警记录）：id,monitor_id,severity,status(firing/recovered),error_type,error_msg,
   http_code,fingerprint,first_seen,last_seen,recovered_at,duration_sec,notify_count,created_at。
6. notify_channel（通知渠道）：id,name,type(email/wecom_robot),config(JSON),enabled。
7. notify_log（通知回执）：id,alert_id,channel_id,channel_type,receiver,content,success,
   error_msg,cost_ms,sent_at。

要求：
- Flyway 脚本放 src/main/resources/db/migration，命名 V1__init.sql。
- 字符集 utf8mb4，InnoDB，字段加 COMMENT。
- 生成实体（Lombok @Data）、Mapper（继承 BaseMapper）、必要的枚举（MonitorStatus、ErrorType、Severity、ChannelType、AssertSource、AssertOperator）。
- JSON 字段用 MyBatis-Plus 的 JacksonTypeHandler 映射为对象/Map。
- 插入少量种子数据（一个 service、一个 email 渠道占位、一个 wecom_robot 渠道占位）。

产出：迁移脚本 + 实体 + Mapper，应用启动时自动建表。
```

---

## 4. 阶段 2：监控项 CRUD API

```text
【阶段2 提示词】（附带公共上下文）

任务：实现 service 与 monitor 的管理 API（参考 docs/05-API接口设计.md）。

接口（统一前缀 /api/v1，返回 Result<T>）：
- 服务：GET/POST /services，GET/PUT/DELETE /services/{id}。
- 监控项：
  - GET /monitors（分页，支持按 service_id/env/status/关键字过滤）
  - POST /monitors（创建，含 assertions 列表，事务保存 monitor + monitor_assertion）
  - GET /monitors/{id}（含断言）
  - PUT /monitors/{id}（更新，含断言全量替换）
  - DELETE /monitors/{id}
  - POST /monitors/{id}/enable、/disable

要求：
- 用 DTO 接收/返回，校验注解（@NotBlank/@NotNull/@Min 等）：url 必填且为 http/https，
  method 限定 GET/POST/PUT/DELETE/HEAD，interval_sec>=10，timeout_ms 在 100~60000。
- 分页用 MyBatis-Plus Page。
- service 删除前校验是否有关联 monitor。
- Swagger 注解完善，便于联调。

产出：可在 Swagger UI 完成监控项的增删改查与启停。
```

---

## 5. 阶段 3：调度 + HTTP 探测执行

```text
【阶段3 提示词】（附带公共上下文）

任务：实现定时调度与 HTTP 探测执行器，把探测结果写入 probe_result。

要求：
1. 调度：用 @Scheduled(fixedDelay=10s) 的「调度扫描器」，每 10 秒扫描 enabled=1 的监控项，
   根据各自 interval_sec 判断是否到达下次探测时间（用内存 Map<monitorId, nextRunTs> 记录，
   重启后按 now 重新计算即可）。到点的监控项提交到探测线程池执行。
   - 线程池：可配置核心/最大线程数（monitor.probe.pool-size，默认 20），队列有界，拒绝策略 CallerRuns。
   - 预留 ShedLock 注解位置（注释说明后续多实例如何加分布式锁），MVP 不强制启用。
2. 探测执行器 ProbeExecutor（用 OkHttp）：
   - 按 monitor 配置构造请求（method/headers/body/timeout/verify_tls）。
   - 记录 latency_ms（总耗时），失败时区分 error_type：timeout/dns/conn/tls/http/assert/unknown。
   - 单次任务内失败重试 retry_times 次（间隔 1s），全部失败才算本次失败。
   - 截断保存响应体片段 resp_snippet（最多 1KB，做基础脱敏：屏蔽形似 token/手机号的内容）。
   - 返回 ProbeOutcome（success、httpCode、latencyMs、errorType、errorMsg、bodySnippet、responseBody 供断言用）。
3. 把每次探测结果写入 probe_result。
4. 提供 POST /api/v1/monitors/{id}/test：同步执行一次探测并返回 ProbeOutcome（不依赖调度）。

注意：探测判定（断言/状态机/告警）放到阶段4，本阶段先把「探测成功与否（仅连通+状态码 2xx）」算出来并落库即可。

产出：启用监控项后，probe_result 持续产生记录；/test 接口可即时返回探测结果。
```

---

## 6. 阶段 4：断言判定 + 状态机 + 告警生成

```text
【阶段4 提示词】（附带公共上下文）

任务：实现健康判定（断言）、状态机与告警/恢复事件生成。

1. 断言判定 AssertionEvaluator：
   - 输入 ProbeOutcome + monitor 的 assertion 列表，全部断言通过才算健康（AND）。
   - source=status_code：比较 http_code（支持 eq/neq/in，如 in 200,201,204）。
   - source=latency：比较 latency_ms（lt/gt）。
   - source=body_json：用 JsonPath 取值（expression 如 $.code）再按 operator 比较（eq/neq/contains/in）。
   - 若监控项无断言，默认规则：连通且 http_code 在 2xx 即健康。
   - 任一断言失败：success=false，error_type=assert，error_msg 说明哪条断言未过。
   - 用断言后的最终结果更新 probe_result.success。

2. 状态机 StateMachine（基于 monitor.status 与连续计数，计数存内存 Map 即可，按 monitorId）：
   - 状态：UP / PENDING / DOWN / RECOVERING。
   - 失败累加 failCount；达到 alert_fail_threshold → 置 DOWN，触发【告警事件】。
   - DOWN 期间成功累加 successCount；达到 alert_recover_threshold → 置 UP，触发【恢复事件】。
   - 更新 monitor.status 到数据库。

3. 告警生成 AlertService：
   - 告警事件：按 fingerprint=hash(monitorId+errorType) 查找是否有 firing 中的 alert；
     - 无则新建 alert(status=firing,first_seen=now,last_seen=now)；
     - 有则更新 last_seen。触发通知（阶段5），并 notify_count++。
   - 恢复事件：把对应 firing 的 alert 置 recovered，写 recovered_at、duration_sec，触发恢复通知。
   - 重复通知收敛：firing 未恢复期间，仅当距上次通知 >= renotify_interval_sec 才再次通知。

产出：接口故障连续 N 次后生成 firing 告警；恢复后置 recovered；告警/恢复均触发通知调用（下阶段实现真正发送）。
```

---

## 7. 阶段 5：通知渠道（邮件 + 企业微信群机器人）

```text
【阶段5 提示词】（附带公共上下文）

任务：实现通知网关与两个渠道，把告警/恢复事件真正发出去，并记录回执。

1. 统一抽象 NotifyChannel 接口：ChannelType type(); SendResult send(NotifyMessage msg)。
   NotifyMessage 含：title、textContent、markdownContent、收件人列表（邮件用）。

2. 模板渲染 TemplateRenderer：
   - 变量：service、monitor_name、url、method、severity、status、error、http_code、
     latency、first_seen、duration、detail_url、now。
   - 告警模板、恢复模板各一套（文本 + 企业微信 markdown）。参考 docs/02-通知渠道设计.md 的示例。

3. EmailChannel（type=email）：
   - 用 JavaMailSender 发送 HTML 邮件；config 取自 notify_channel.config（host/port/tls/username/password/from）。
   - 失败指数退避重试最多 3 次。

4. WecomRobotChannel（type=wecom_robot）：
   - config.webhook 为群机器人地址；用 OkHttp POST markdown 消息。
   - 解析企业微信返回 errcode，非 0 视为失败；遵守频率限制（简单令牌桶或最小发送间隔）。

5. 通知网关 NotificationGateway：
   - 查询 enabled 的 notify_channel，对每个渠道渲染并发送。
   - 每次发送写 notify_log（成功/失败/耗时/错误/retry_count）。
   - 主渠道（wecom_robot）连续失败时，记录日志（MVP 不强制做兜底切换，留 TODO）。
   - 敏感配置从环境变量/加密读取，日志中脱敏 webhook key 与密码。

6. 配置渠道的方式：MVP 直接通过 /api/v1/channels 接口或种子数据写入 notify_channel；
   提供 POST /api/v1/channels/{id}/test 发送一条测试通知验证连通。

产出：制造一个必然失败的监控项（如指向不存在的 URL），能在邮件与企业微信群收到告警；
恢复后能收到恢复通知；notify_log 有完整回执。
```

---

## 8. 阶段 6：联调、配置与文档收尾

```text
【阶段6 提示词】（附带公共上下文）

任务：端到端联调、补充配置与运行文档、基础健壮性。

要求：
1. 提供 docker-compose.yml：MySQL 8 + 本应用，便于一键起本地环境。
2. 完善 README：环境变量清单（DB、SMTP、企业微信 webhook 通过渠道配置）、启动步骤、
   Swagger 地址、如何新增一个监控项并验证告警的完整操作流程。
3. 增加少量单元测试：AssertionEvaluator（断言比较）、StateMachine（连续失败/恢复转换）、
   TemplateRenderer（变量替换）。
4. 健壮性：探测异常不影响调度线程；数据库/网络异常有日志与降级；
   通知发送异常不影响告警入库。
5. 自检清单（在 README 写明）：
   - [ ] 新建监控项→自动探测→probe_result 落库
   - [ ] 故障接口连续失败→生成 firing 告警→收到邮件+企业微信
   - [ ] 接口恢复→alert 置 recovered→收到恢复通知
   - [ ] renotify 收敛生效（未恢复期间按间隔再次通知）

产出：可本地一键运行并完成端到端验证的 MVP。
```

---

## 9. 给 AI 代理的「总控提示词」（可选，作为系统提示）

```text
你是一名资深 Java 后端工程师，正在按里程碑实现一个 API 接口级监控告警系统的 MVP。
技术栈：Java 17 + Spring Boot 3 + MyBatis-Plus + MySQL + Flyway + OkHttp + JavaMail + 企业微信群机器人，全部开源。
请严格按我给出的「阶段X 提示词」逐步实现，遵循分层架构与统一返回体，代码可编译可运行，
每个阶段结束后输出：改动文件清单 + 本地验证步骤，并停下等待我确认再进入下一阶段。
不要提前实现超出当前阶段范围的功能；不要引入收费/闭源依赖；注释克制。
遇到设计取舍时，优先选择简单、可演进的方案，并简要说明理由。
```

---

## 10. 使用建议

1. 先发「第 9 节总控提示词」设定角色与全局约束。
2. 再依次发「阶段 0 → 阶段 6」，每个阶段附上「第 1 节公共上下文」。
3. 每阶段完成后按其「产出/自检清单」验证，通过后再继续。
4. 企业微信群机器人 Webhook 与 SMTP 账号请准备好（群机器人在企业微信群「添加群机器人」获取，邮件用企业邮箱授权码）。
