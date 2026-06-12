package com.company.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监控项本地登记/映射（与 HertzBeat 监控的对应关系）。
 */
@Data
@TableName("monitor_ref")
public class MonitorRef {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** HertzBeat 监控ID */
    private Long hzbMonitorId;

    private String name;

    private String url;

    private String method;

    private String serviceName;

    private String env;

    /** 告警等级 P0/P1/P2/P3 */
    private String severity;

    private String owner;

    private Integer enabled;

    private LocalDateTime syncedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
