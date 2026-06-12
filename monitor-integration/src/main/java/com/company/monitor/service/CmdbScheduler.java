package com.company.monitor.service;

import com.company.monitor.config.CmdbProperties;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * CMDB 定时同步：配置了 cmdb.url 且 sync-interval-ms>0 时，周期性从 CMDB 同步服务负责人。
 */
@Slf4j
@Component
public class CmdbScheduler {

    private final CmdbProperties properties;
    private final CmdbService cmdbService;

    public CmdbScheduler(CmdbProperties properties, CmdbService cmdbService) {
        this.properties = properties;
        this.cmdbService = cmdbService;
    }

    @Scheduled(fixedDelayString = "${cmdb.sync-interval-ms:3600000}", initialDelay = 30000)
    @SchedulerLock(name = "cmdb-sync", lockAtMostFor = "PT10M", lockAtLeastFor = "PT10S")
    public void sync() {
        if (!properties.isScheduledEnabled() || properties.getUrl() == null || properties.getUrl().isBlank()) {
            return;
        }
        try {
            Map<String, Object> r = cmdbService.syncFromUrl(properties.getUrl(),
                    properties.getServiceField(), properties.getWecomField(), properties.getEmailField());
            log.info("CMDB 定时同步完成: {}", r);
        } catch (Exception e) {
            log.warn("CMDB 定时同步失败: {}", e.getMessage());
        }
    }
}
