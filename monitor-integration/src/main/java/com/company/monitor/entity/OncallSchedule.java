package com.company.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oncall_schedule")
public class OncallSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 服务名；为空=全局值班 */
    private String serviceName;

    private String wecomUserids;

    private String emailList;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Integer enabled;

    private LocalDateTime createdAt;
}
