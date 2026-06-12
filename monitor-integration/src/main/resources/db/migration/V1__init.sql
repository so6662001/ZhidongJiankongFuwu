-- 集成层自有元数据与告警归档表（MVP）

CREATE TABLE IF NOT EXISTS `monitor_ref` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `hzb_monitor_id` BIGINT      NULL COMMENT 'HertzBeat 监控ID',
  `name`         VARCHAR(256) NOT NULL COMMENT '监控项名称',
  `url`          VARCHAR(1024) NOT NULL COMMENT '目标接口URL',
  `method`       VARCHAR(8)   NOT NULL DEFAULT 'GET' COMMENT '请求方法',
  `service_name` VARCHAR(128) NULL COMMENT '所属服务/业务线',
  `env`          VARCHAR(16)  NOT NULL DEFAULT 'prod' COMMENT '环境',
  `severity`     VARCHAR(8)   NOT NULL DEFAULT 'P2' COMMENT '告警等级 P0/P1/P2/P3',
  `owner`        VARCHAR(128) NULL COMMENT '负责人',
  `enabled`      TINYINT      NOT NULL DEFAULT 1,
  `synced_at`    DATETIME     NULL COMMENT '最近同步到HertzBeat时间',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_name` (`service_name`, `name`),
  KEY `idx_hzb` (`hzb_monitor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控项本地登记/映射';

CREATE TABLE IF NOT EXISTS `service_owner` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `service_name`  VARCHAR(128) NOT NULL COMMENT '服务/业务线',
  `wecom_userids` VARCHAR(512) NULL COMMENT '企业微信userid，逗号分隔',
  `email_list`    VARCHAR(512) NULL COMMENT '邮件接收人，逗号分隔',
  `enabled`       TINYINT      NOT NULL DEFAULT 1,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service` (`service_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务->负责人映射';

CREATE TABLE IF NOT EXISTS `alert` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `hzb_monitor_id` BIGINT      NULL COMMENT 'HertzBeat 监控ID',
  `monitor_name`  VARCHAR(256) NULL,
  `url`           VARCHAR(1024) NULL,
  `service_name`  VARCHAR(128) NULL,
  `severity`      VARCHAR(8)   NOT NULL DEFAULT 'P2',
  `status`        VARCHAR(16)  NOT NULL DEFAULT 'firing' COMMENT 'firing/recovered',
  `content`       VARCHAR(2048) NULL COMMENT '告警内容',
  `target`        VARCHAR(256) NULL COMMENT '触发指标/目标',
  `trigger_times` INT          NOT NULL DEFAULT 1,
  `fingerprint`   VARCHAR(64)  NOT NULL COMMENT '去重指纹',
  `first_seen`    DATETIME     NOT NULL,
  `last_seen`     DATETIME     NOT NULL,
  `recovered_at`  DATETIME     NULL,
  `duration_sec`  INT          NULL,
  `notify_count`  INT          NOT NULL DEFAULT 0,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_hzb` (`hzb_monitor_id`),
  KEY `idx_status` (`status`),
  KEY `idx_fingerprint` (`fingerprint`),
  KEY `idx_service_time` (`service_name`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警归档';

CREATE TABLE IF NOT EXISTS `notify_log` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `alert_id`     BIGINT       NOT NULL,
  `channel_type` VARCHAR(32)  NOT NULL COMMENT 'wecom_app/email',
  `receiver`     VARCHAR(512) NULL,
  `content`      MEDIUMTEXT   NULL,
  `success`      TINYINT      NOT NULL,
  `error_msg`    VARCHAR(512) NULL,
  `cost_ms`      INT          NULL,
  `retry_count`  INT          NOT NULL DEFAULT 0,
  `sent_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_alert` (`alert_id`),
  KEY `idx_sent_at` (`sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知发送回执';
