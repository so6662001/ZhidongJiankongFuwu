package com.company.monitor.dto;

import lombok.Data;

/**
 * 更新本地监控项业务属性。
 */
@Data
public class MonitorRefUpdateRequest {

    private String serviceName;

    private String severity;

    private String owner;

    private Integer enabled;
}
