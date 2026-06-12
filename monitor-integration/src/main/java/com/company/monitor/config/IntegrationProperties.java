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

    /** 管理 API 鉴权 Key（请求头 X-Api-Key）。为空则不鉴权（仅建议内网/测试），强烈建议生产配置 */
    private String apiKey;

    /** OpenAPI 拉取允许的 host 白名单（逗号分隔域名）。为空表示不限制 host（仍会拦截环回/内网保留地址） */
    private java.util.List<String> openapiAllowedHosts = new java.util.ArrayList<>();

    /** 是否允许拉取私网/内网地址的 OpenAPI 文档（默认 false，防 SSRF） */
    private boolean openapiAllowPrivateNetwork = false;

    /**
     * 本服务告警 Webhook 的可达地址（供 HertzBeat 回调）。
     * 配置后，导入时会自动在 HertzBeat 创建 webhook 接收人+全量转发策略，打通"立即通知"。
     * 例：http://monitor-integration:8080/api/v1/webhook/hertzbeat
     */
    private String selfWebhookUrl;
}
