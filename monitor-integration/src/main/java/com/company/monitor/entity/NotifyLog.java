package com.company.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notify_log")
public class NotifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long alertId;

    /** wecom_app / email */
    private String channelType;

    private String receiver;

    private String content;

    private Integer success;

    private String errorMsg;

    private Integer costMs;

    private Integer retryCount;

    private LocalDateTime sentAt;
}
