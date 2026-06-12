package com.company.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.monitor.entity.Alert;
import com.company.monitor.entity.MaintenanceWindow;
import com.company.monitor.mapper.MaintenanceWindowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 维护窗口/静默判定：判断某告警当前是否处于维护静默期内。
 */
@Slf4j
@Service
public class SilenceService {

    private final MaintenanceWindowMapper mapper;

    public SilenceService(MaintenanceWindowMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 当前是否应静默该告警（命中任一启用且生效中的维护窗口）。
     */
    public boolean isSilenced(Alert alert) {
        LocalDateTime now = LocalDateTime.now();
        List<MaintenanceWindow> active = mapper.selectList(new QueryWrapper<MaintenanceWindow>()
                .eq("enabled", 1)
                .le("start_at", now)
                .ge("end_at", now));
        for (MaintenanceWindow w : active) {
            if (matches(w, alert)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(MaintenanceWindow w, Alert alert) {
        String type = w.getScopeType() == null ? "global" : w.getScopeType();
        return switch (type) {
            case "global" -> true;
            case "service" -> w.getScopeValue() != null && w.getScopeValue().equals(alert.getServiceName());
            case "monitor" -> w.getScopeValue() != null && w.getScopeValue().equals(alert.getMonitorName());
            default -> false;
        };
    }
}
