package com.company.monitor.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 启动时对安全相关配置做提醒（不阻断启动，仅告警）。
 */
@Slf4j
@Component
public class SecurityStartupCheck {

    private final IntegrationProperties properties;

    public SecurityStartupCheck(IntegrationProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void check() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("[安全提醒] 未配置 integration.api-key(API_KEY)，管理 API 处于无鉴权状态，生产环境请务必配置！");
        }
        if (properties.getWebhookToken() == null || properties.getWebhookToken().isBlank()
                || "change-me".equals(properties.getWebhookToken())) {
            log.warn("[安全提醒] integration.webhook-token 使用默认/空值，请修改 WEBHOOK_TOKEN！");
        }
    }
}
