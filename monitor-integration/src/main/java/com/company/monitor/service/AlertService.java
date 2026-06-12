package com.company.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.monitor.config.IntegrationProperties;
import com.company.monitor.entity.Alert;
import com.company.monitor.entity.MonitorRef;
import com.company.monitor.mapper.AlertMapper;
import com.company.monitor.mapper.MonitorRefMapper;
import com.company.monitor.notify.NotificationGateway;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * 解析 HertzBeat 告警 Webhook（GroupAlert，Alertmanager 风格），归档并触发通知。
 */
@Slf4j
@Service
public class AlertService {

    private final AlertMapper alertMapper;
    private final MonitorRefMapper monitorRefMapper;
    private final NotificationGateway notificationGateway;
    private final IntegrationProperties properties;
    private final SilenceService silenceService;

    public AlertService(AlertMapper alertMapper, MonitorRefMapper monitorRefMapper,
                        NotificationGateway notificationGateway, IntegrationProperties properties,
                        SilenceService silenceService) {
        this.alertMapper = alertMapper;
        this.monitorRefMapper = monitorRefMapper;
        this.notificationGateway = notificationGateway;
        this.properties = properties;
        this.silenceService = silenceService;
    }

    /** 维护窗口内静默：仍归档，但跳过通知。 */
    private void notifyUnlessSilenced(Alert alert, boolean recovered) {
        if (silenceService != null && silenceService.isSilenced(alert)) {
            log.info("命中维护窗口，静默通知 alertId={} service={} monitor={}",
                    alert.getId(), alert.getServiceName(), alert.getMonitorName());
            return;
        }
        notificationGateway.notify(alert, recovered);
    }

    /**
     * 处理一条 webhook payload，返回处理的告警条数。
     */
    public int handleWebhook(JsonNode payload) {
        String groupStatus = payload.path("status").asText("firing");
        JsonNode commonLabels = payload.path("commonLabels");
        String commonSeverity = textOrNull(commonLabels.path("severity"));

        JsonNode alerts = payload.path("alerts");
        if (!alerts.isArray() || alerts.isEmpty()) {
            log.warn("告警 payload 无 alerts 数组，忽略");
            return 0;
        }
        int handled = 0;
        for (JsonNode a : alerts) {
            try {
                handleSingle(a, groupStatus, commonSeverity);
                handled++;
            } catch (Exception e) {
                log.error("处理单条告警异常: {}", e.getMessage(), e);
            }
        }
        return handled;
    }

    private void handleSingle(JsonNode a, String groupStatus, String commonSeverity) {
        Map<String, String> labels = toMap(a.path("labels"));
        String monitorName = labels.getOrDefault("instancename", labels.get("monitor_name"));
        String alertName = labels.getOrDefault("alertname", "alert");
        String content = a.path("content").asText(null);
        int triggerTimes = a.path("triggerTimes").asInt(1);
        boolean recovered = "resolved".equalsIgnoreCase(groupStatus)
                || (a.hasNonNull("endAt") && !a.path("endAt").asText().isBlank());

        String fingerprint = computeFingerprint(labels, alertName);

        // 关联本地登记的监控项，补充业务字段
        MonitorRef ref = monitorName != null ? findRefByName(monitorName) : null;

        Alert existingFiring = alertMapper.selectOne(new QueryWrapper<Alert>()
                .eq("fingerprint", fingerprint).eq("status", "firing").last("limit 1"));

        if (recovered) {
            if (existingFiring != null) {
                existingFiring.setStatus("recovered");
                existingFiring.setRecoveredAt(LocalDateTime.now());
                if (existingFiring.getFirstSeen() != null) {
                    existingFiring.setDurationSec((int) Duration.between(
                            existingFiring.getFirstSeen(), LocalDateTime.now()).getSeconds());
                }
                alertMapper.updateById(existingFiring);
                notifyUnlessSilenced(existingFiring, true);
                log.info("告警恢复 fingerprint={}", fingerprint);
            } else {
                log.info("收到恢复事件但无对应 firing 告警，忽略 fingerprint={}", fingerprint);
            }
            return;
        }

        // firing
        if (existingFiring != null) {
            existingFiring.setLastSeen(LocalDateTime.now());
            existingFiring.setTriggerTimes(triggerTimes);
            if (content != null) {
                existingFiring.setContent(truncate(content, 2048));
            }
            boolean shouldRenotify = existingFiring.getLastSeen() != null
                    && needRenotify(existingFiring);
            alertMapper.updateById(existingFiring);
            if (shouldRenotify) {
                existingFiring.setNotifyCount((existingFiring.getNotifyCount() == null ? 0 : existingFiring.getNotifyCount()) + 1);
                alertMapper.updateById(existingFiring);
                notifyUnlessSilenced(existingFiring, false);
            }
        } else {
            Alert alert = new Alert();
            alert.setFingerprint(fingerprint);
            alert.setStatus("firing");
            alert.setMonitorName(monitorName);
            alert.setContent(truncate(content, 2048));
            alert.setTriggerTimes(triggerTimes);
            alert.setTarget(labels.get("instance"));
            alert.setSeverity(resolveSeverity(ref, commonSeverity, labels));
            if (ref != null) {
                alert.setHzbMonitorId(ref.getHzbMonitorId());
                alert.setServiceName(ref.getServiceName());
                alert.setUrl(ref.getUrl());
            }
            // 用接收时刻作为首次发现时间，避免 payload 时区与本服务不一致导致持续时长计算异常
            alert.setFirstSeen(LocalDateTime.now());
            alert.setLastSeen(LocalDateTime.now());
            // 显式设置 created_at（与 first_seen 同源），避免 DB 时区与本服务不一致导致统计/趋势偏差
            alert.setCreatedAt(LocalDateTime.now());
            alert.setNotifyCount(1);
            alertMapper.insert(alert);
            notifyUnlessSilenced(alert, false);
            log.info("新增 firing 告警 id={} fingerprint={}", alert.getId(), fingerprint);
        }
    }

    private boolean needRenotify(Alert firing) {
        // 距上次创建/通知超过 renotify 间隔则再次通知（简化：用 lastSeen-firstSeen 周期判断）
        if (firing.getFirstSeen() == null) {
            return false;
        }
        long sinceFirst = Duration.between(firing.getFirstSeen(), LocalDateTime.now()).getSeconds();
        long interval = properties.getRenotifyIntervalSeconds();
        if (interval <= 0) {
            return false;
        }
        int count = firing.getNotifyCount() == null ? 0 : firing.getNotifyCount();
        // 已通知 count 次，当持续时间跨过下一个间隔窗口时再次通知
        return sinceFirst >= (long) count * interval;
    }

    private String resolveSeverity(MonitorRef ref, String commonSeverity, Map<String, String> labels) {
        if (ref != null && ref.getSeverity() != null) {
            return ref.getSeverity();
        }
        String s = commonSeverity != null ? commonSeverity : labels.get("severity");
        if (s == null) {
            return "P2";
        }
        return switch (s.toLowerCase()) {
            case "critical" -> "P0";
            case "warning" -> "P1";
            case "info" -> "P3";
            default -> s;
        };
    }

    private MonitorRef findRefByName(String name) {
        return monitorRefMapper.selectOne(new QueryWrapper<MonitorRef>()
                .eq("name", name).last("limit 1"));
    }

    private String computeFingerprint(Map<String, String> labels, String alertName) {
        TreeMap<String, String> sorted = new TreeMap<>(labels);
        StringBuilder sb = new StringBuilder(alertName).append('|');
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(';');
        }
        return md5(sb.toString());
    }

    private String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private Map<String, String> toMap(JsonNode node) {
        Map<String, String> map = new TreeMap<>();
        if (node != null && node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = node.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                map.put(e.getKey(), e.getValue().asText());
            }
        }
        return map;
    }

    private String textOrNull(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() ? node.asText() : null;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
