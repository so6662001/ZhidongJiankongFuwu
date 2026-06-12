package com.company.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alert")
public class Alert {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long hzbMonitorId;

    private String monitorName;

    private String url;

    private String serviceName;

    private String severity;

    /** firing / recovered */
    private String status;

    private String content;

    private String target;

    private Integer triggerTimes;

    private String fingerprint;

    private LocalDateTime firstSeen;

    private LocalDateTime lastSeen;

    private LocalDateTime recoveredAt;

    private Integer durationSec;

    private Integer notifyCount;

    private LocalDateTime createdAt;
}
