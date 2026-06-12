package com.company.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CMDB 对接配置。配置了 url 后可定时同步服务负责人。
 */
@Data
@Component
@ConfigurationProperties(prefix = "cmdb")
public class CmdbProperties {

    /** CMDB 拉取地址；为空则不启用定时同步（仍可手动调用 /api/v1/cmdb/sync） */
    private String url;

    private String serviceField;
    private String wecomField;
    private String emailField;

    /** 是否启用定时同步（需同时配置 url） */
    private boolean scheduledEnabled = false;

    /** 定时同步间隔（毫秒），默认 1 小时 */
    private long syncIntervalMs = 3600000;
}
