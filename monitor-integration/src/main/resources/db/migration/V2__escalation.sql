-- 告警升级标记
ALTER TABLE `alert`
  ADD COLUMN `escalated` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已升级通知',
  ADD COLUMN `escalated_at` DATETIME NULL COMMENT '升级通知时间';
