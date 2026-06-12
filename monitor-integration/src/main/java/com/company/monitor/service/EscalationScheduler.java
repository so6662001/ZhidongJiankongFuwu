package com.company.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.monitor.config.IntegrationProperties;
import com.company.monitor.entity.Alert;
import com.company.monitor.mapper.AlertMapper;
import com.company.monitor.notify.NotificationGateway;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 告警升级：定时扫描长时间未恢复的 firing 告警，向升级接收人再通知一次。
 */
@Slf4j
@Component
public class EscalationScheduler {

    private final AlertMapper alertMapper;
    private final NotificationGateway notificationGateway;
    private final IntegrationProperties properties;

    public EscalationScheduler(AlertMapper alertMapper, NotificationGateway notificationGateway,
                               IntegrationProperties properties) {
        this.alertMapper = alertMapper;
        this.notificationGateway = notificationGateway;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${integration.escalate-scan-interval-ms:60000}")
    @SchedulerLock(name = "escalation-scan", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    public void scan() {
        long after = properties.getEscalateAfterSeconds();
        if (after <= 0) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(after);
        List<Alert> candidates = alertMapper.selectList(new QueryWrapper<Alert>()
                .eq("status", "firing")
                .and(w -> w.eq("escalated", 0).or().isNull("escalated"))
                .le("first_seen", threshold));
        if (candidates.isEmpty()) {
            return;
        }
        List<String> escWecom = merge(notificationGateway.splitList(properties.getEscalateWecomUserids()));
        List<String> escEmail = merge(notificationGateway.splitList(properties.getEscalateEmailList()));

        for (Alert alert : candidates) {
            try {
                // 升级接收人 = 配置的升级人 + 该服务负责人（去重）
                List<String> wecom = new ArrayList<>(escWecom);
                for (String u : notificationGateway.wecomUserIdsForService(alert.getServiceName())) {
                    if (!wecom.contains(u)) {
                        wecom.add(u);
                    }
                }
                List<String> emails = new ArrayList<>(escEmail);
                for (String e : notificationGateway.emailsForService(alert.getServiceName())) {
                    if (!emails.contains(e)) {
                        emails.add(e);
                    }
                }
                long mins = Duration.between(alert.getFirstSeen(), LocalDateTime.now()).toMinutes();
                log.warn("告警升级: alertId={} 已持续 {} 分钟未恢复", alert.getId(), mins);
                notificationGateway.notifyEscalation(alert, wecom, emails);
                alert.setEscalated(1);
                alert.setEscalatedAt(LocalDateTime.now());
                alertMapper.updateById(alert);
            } catch (Exception e) {
                log.error("升级告警 alertId={} 失败: {}", alert.getId(), e.getMessage());
            }
        }
    }

    private List<String> merge(List<String> list) {
        return list != null ? list : new ArrayList<>();
    }
}
