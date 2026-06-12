package com.company.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企业微信自建应用配置（应用消息，精准@人）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wecom")
public class WeComProperties {

    private String corpid;

    private String agentid;

    private String secret;

    /** 默认接收人 userid（逗号分隔），服务未配置负责人时回退使用 */
    private String defaultUserids;

    public boolean isConfigured() {
        return corpid != null && !corpid.isBlank()
                && agentid != null && !agentid.isBlank()
                && secret != null && !secret.isBlank();
    }
}
