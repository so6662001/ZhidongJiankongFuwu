-- ShedLock 分布式锁表（多副本定时任务互斥）
CREATE TABLE IF NOT EXISTS `shedlock` (
  `name`       VARCHAR(64)  NOT NULL,
  `lock_until` TIMESTAMP(3) NOT NULL,
  `locked_at`  TIMESTAMP(3) NOT NULL,
  `locked_by`  VARCHAR(255) NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ShedLock 分布式调度锁';
