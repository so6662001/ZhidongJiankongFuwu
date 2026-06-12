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
        boolean noApiAuth = (properties.getApiKey() == null || properties.getApiKey().isBlank())
                && (properties.getSsoTrustedHeader() == null || properties.getSsoTrustedHeader().isBlank());
        boolean weakWebhook = properties.getWebhookToken() == null || properties.getWebhookToken().isBlank()
                || "change-me".equals(properties.getWebhookToken());

        if (noApiAuth) {
            log.warn("[安全提醒] 未配置 API_KEY 且未配置 SSO 头，管理 API 处于无鉴权状态，生产环境请务必配置！");
        }
        if (weakWebhook) {
            log.warn("[安全提醒] WEBHOOK_TOKEN 使用默认/空值，请修改！");
        }
        if (properties.isSecurityStrict() && (noApiAuth || weakWebhook)) {
            throw new IllegalStateException(
                    "安全严格模式(integration.security-strict=true)下拒绝启动：请配置 API_KEY/SSO 头，并将 WEBHOOK_TOKEN 改为非默认强随机值。");
        }
    }
}
