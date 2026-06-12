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

    /** 企业微信群机器人 Webhook 地址（可选，作为更易配置的真实投递渠道；群播不精准@到 userid） */
    private String robotWebhook;

    /** 自建应用是否已配置（精准@人） */
    public boolean isConfigured() {
        return corpid != null && !corpid.isBlank()
                && agentid != null && !agentid.isBlank()
                && secret != null && !secret.isBlank();
    }

    /** 群机器人是否已配置 */
    public boolean isRobotConfigured() {
        return robotWebhook != null && !robotWebhook.isBlank();
    }
}
