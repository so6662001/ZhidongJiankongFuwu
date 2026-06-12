package com.company.monitor.service;

import com.company.monitor.config.IntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dead Man's Switch：定期向外部看门狗上报心跳。外部在超时未收到时独立告警，
 * 解决"监控系统自身故障无人知晓"的问题。仅在配置了 heartbeat-url 时启用。
 */
@Slf4j
@Component
public class HeartbeatScheduler {

    private final IntegrationProperties properties;
    private final OkHttpClient httpClient;

    public HeartbeatScheduler(IntegrationProperties properties, OkHttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @Scheduled(fixedDelayString = "${integration.heartbeat-interval-ms:60000}")
    public void beat() {
        String url = properties.getHeartbeatUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            Request req = new Request.Builder().url(url).get().build();
            try (Response resp = httpClient.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    log.warn("心跳上报返回非 2xx: {}", resp.code());
                }
            }
        } catch (Exception e) {
            log.warn("心跳上报失败: {}", e.getMessage());
        }
    }
}
