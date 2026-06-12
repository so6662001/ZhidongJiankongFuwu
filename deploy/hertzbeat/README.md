# HertzBeat 部署（阶段 0）

监控引擎 Apache HertzBeat + 元数据库 MySQL + 时序库 VictoriaMetrics 的一键部署。

## 目录

```
deploy/hertzbeat/
├── docker-compose.yml      # HertzBeat + MySQL + VictoriaMetrics
├── conf/application.yml     # HertzBeat 配置覆盖（连 MySQL + VictoriaMetrics）
└── ext-lib/download.sh      # 下载 MySQL JDBC 驱动（官方镜像未内置）
```

## 启动步骤

```bash
# 1) 下载 MySQL 驱动到 ext-lib（HertzBeat 官方镜像未内置 MySQL 驱动，必须步骤）
bash deploy/hertzbeat/ext-lib/download.sh

# 2) 启动（可选：自定义 MySQL 密码、邮件、VictoriaMetrics 账号等环境变量）
cd deploy/hertzbeat
MYSQL_ROOT_PASSWORD=root123456 docker compose up -d

# 3) 查看状态
docker compose ps
docker compose logs -f hertzbeat
```

## 访问

| 项 | 地址 | 说明 |
| --- | --- | --- |
| HertzBeat Web 控制台 | http://localhost:1157 | 默认账号 `admin` / `hertzbeat`，**首次登录请改密** |
| HertzBeat API 文档 | http://localhost:1157/swagger-ui.html 或 /doc.html | 以实际版本为准 |
| VictoriaMetrics | http://localhost:8428 | `/health` 返回 OK 即正常 |
| MySQL | localhost:3306 | 库 `hertzbeat`，账号 root |

登录 API（集成层即按此对接，已验证）：

```bash
curl -X POST http://localhost:1157/api/account/auth/form \
  -H 'Content-Type: application/json' \
  -d '{"identifier":"admin","credential":"hertzbeat"}'
# 返回 { "data": { "token": "...", "refreshToken": "..." }, "code": 0 }
```

## 关键说明（实测要点）

1. **MySQL 驱动**：`apache/hertzbeat` 官方镜像默认用内置 H2/duckdb，**不含 MySQL 驱动**。
   要连 MySQL，必须把 `mysql-connector-j-*.jar` 放进 `ext-lib/`（已挂载到容器 `/opt/hertzbeat/ext-lib`）。
2. **时序库 VictoriaMetrics**：在 `conf/application.yml` 中已 `warehouse.store.duckdb.enabled=false`、
   `warehouse.store.victoria-metrics.enabled=true`，并指向 `http://victoria-metrics:8428`。
   监控产生的指标历史/趋势数据会落到 VictoriaMetrics。
3. **配置随版本同步**：`conf/application.yml` 基于某版本官方默认文件改写，升级 HertzBeat 大版本时，
   建议从新镜像取出 `/opt/hertzbeat/config/application.yml` 重新对照合并。

## 验证 VictoriaMetrics 已生效

在 HertzBeat 新增一个 HTTP 监控后，等待采集，几个周期后在监控详情的历史图表能看到曲线；
或直接查询 VictoriaMetrics：

```bash
curl 'http://localhost:8428/api/v1/label/__name__/values' | head
```

## 沙箱/无 Docker 网络环境的替代验证

若所在环境 Docker 的 bridge/NAT 不可用（如部分受限沙箱），可用 host 网络逐个启动并把
`conf/application.yml` 中的 `mysql`、`victoria-metrics` 主机名替换为 `127.0.0.1` 进行验证
（本项目已用该方式实测通过：HertzBeat 启动正常、API 登录可用、MySQL 自动建表、集成层成功对接）。
