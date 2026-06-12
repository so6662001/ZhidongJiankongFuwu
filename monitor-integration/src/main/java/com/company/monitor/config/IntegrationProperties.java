package com.company.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 集成层自身配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "integration")
public class IntegrationProperties {

    /** HertzBeat 告警 Webhook 校验密钥（请求头 X-Webhook-Token） */
    private String webhookToken = "change-me";

    /** 详情页基础地址（用于通知中的跳转链接） */
    private String detailBaseUrl = "http://localhost:1157";

    /** 未恢复期间再次通知的最小间隔（秒） */
    private long renotifyIntervalSeconds = 600;
}
