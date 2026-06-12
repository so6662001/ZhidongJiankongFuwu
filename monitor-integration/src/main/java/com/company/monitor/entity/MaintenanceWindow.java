package com.company.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("maintenance_window")
public class MaintenanceWindow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** global / service / monitor */
    private String scopeType;

    private String scopeValue;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private String reason;

    private Integer enabled;

    private String createdBy;

    private LocalDateTime createdAt;
}
