-- 告警认领（Ack）
ALTER TABLE `alert`
  ADD COLUMN `acked_by` VARCHAR(64) NULL COMMENT '认领人',
  ADD COLUMN `acked_at` DATETIME NULL COMMENT '认领时间',
  ADD COLUMN `remark` VARCHAR(512) NULL COMMENT '处理备注';
