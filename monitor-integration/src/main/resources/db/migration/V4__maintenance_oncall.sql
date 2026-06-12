-- 维护窗口 / 静默
CREATE TABLE IF NOT EXISTS `maintenance_window` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(128) NOT NULL COMMENT '名称',
  `scope_type`  VARCHAR(16)  NOT NULL DEFAULT 'global' COMMENT 'global/service/monitor',
  `scope_value` VARCHAR(256) NULL COMMENT 'service 名 或 monitor 名；global 留空',
  `start_at`    DATETIME     NOT NULL,
  `end_at`      DATETIME     NOT NULL,
  `reason`      VARCHAR(512) NULL,
  `enabled`     TINYINT      NOT NULL DEFAULT 1,
  `created_by`  VARCHAR(64)  NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_time` (`start_at`, `end_at`),
  KEY `idx_scope` (`scope_type`, `scope_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维护窗口/静默';

-- 值班排班
CREATE TABLE IF NOT EXISTS `oncall_schedule` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(128) NOT NULL COMMENT '排班名称',
  `service_name`  VARCHAR(128) NULL COMMENT '服务名；为空表示全局值班',
  `wecom_userids` VARCHAR(512) NULL COMMENT '当班企业微信 userid，逗号分隔',
  `email_list`    VARCHAR(512) NULL COMMENT '当班邮件，逗号分隔',
  `start_at`      DATETIME     NOT NULL,
  `end_at`        DATETIME     NOT NULL,
  `enabled`       TINYINT      NOT NULL DEFAULT 1,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_svc_time` (`service_name`, `start_at`, `end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='值班排班';
