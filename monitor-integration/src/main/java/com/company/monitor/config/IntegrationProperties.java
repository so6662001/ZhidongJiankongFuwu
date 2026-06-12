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

    /**
     * 通知发送模式：
     * - failover：优先企业微信，失败/不可用时兜底邮件（默认）
     * - all：所有已配置渠道都发
     */
    private String notifyMode = "failover";

    /** 告警升级：firing 持续超过该秒数仍未恢复则升级通知（<=0 关闭） */
    private long escalateAfterSeconds = 1800;

    /** 升级通知额外接收人：企业微信 userid（逗号分隔） */
    private String escalateWecomUserids;

    /** 升级通知额外接收人：邮件（逗号分隔） */
    private String escalateEmailList;

    /**
     * SSO 可信身份请求头（如 X-Auth-User），由前置 SSO 网关注入。
     * 配置后，携带该非空头的请求视为已认证（与 API Key 二选一通过）。
     * 安全前提：网关必须强制 SSO 并剥离客户端伪造的同名头。
     */
    private String ssoTrustedHeader;

    /**
     * Dead Man's Switch 心跳上报地址（如 Healthchecks.io ping URL）。
     * 配置后按周期上报；外部看门狗在超时未收到心跳时独立告警（避免"监控系统挂了没人知道"）。
     */
    private String heartbeatUrl;
}
