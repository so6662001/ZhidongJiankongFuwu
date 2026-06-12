package com.company.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("service_owner")
public class ServiceOwner {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String serviceName;

    /** 企业微信 userid，逗号分隔 */
    private String wecomUserids;

    /** 邮件接收人，逗号分隔 */
    private String emailList;

    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
