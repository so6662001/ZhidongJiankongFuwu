package com.company.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.monitor.entity.Alert;
import com.company.monitor.mapper.AlertMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SLA / 可用率计算：基于告警记录中的故障时段（按监控合并区间）估算可用率。
 *
 * <p>说明：downtime 取所有告警(firing 段)合并后的时长；availability = (窗口 - downtime)/窗口。
 * 同一监控多类告警重叠时按区间合并，避免重复计算。
 */
@Service
public class SlaService {

    private final AlertMapper alertMapper;

    public SlaService(AlertMapper alertMapper) {
        this.alertMapper = alertMapper;
    }

    public Map<String, Object> computeSla(String serviceName, int hours) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(hours <= 0 ? 24 : hours);
        long windowSec = Duration.between(from, to).getSeconds();

        QueryWrapper<Alert> qw = new QueryWrapper<>();
        if (serviceName != null && !serviceName.isBlank()) {
            qw.eq("service_name", serviceName);
        }
        // 与窗口有交叠的告警：first_seen <= to 且 (recovered_at 为空 或 >= from)
        qw.le("first_seen", to);
        qw.and(w -> w.isNull("recovered_at").or().ge("recovered_at", from));
        List<Alert> alerts = alertMapper.selectList(qw);

        // 按监控名分组
        Map<String, List<long[]>> intervalsByMonitor = new LinkedHashMap<>();
        for (Alert a : alerts) {
            LocalDateTime start = a.getFirstSeen() == null ? from : a.getFirstSeen();
            LocalDateTime end = a.getRecoveredAt() == null ? to : a.getRecoveredAt();
            if (start.isBefore(from)) {
                start = from;
            }
            if (end.isAfter(to)) {
                end = to;
            }
            if (!end.isAfter(start)) {
                continue;
            }
            String key = a.getMonitorName() != null ? a.getMonitorName() : ("monitor#" + a.getHzbMonitorId());
            intervalsByMonitor.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new long[]{toEpoch(start), toEpoch(end)});
        }

        List<Map<String, Object>> monitors = new ArrayList<>();
        double worst = 100.0;
        for (Map.Entry<String, List<long[]>> e : intervalsByMonitor.entrySet()) {
            long downtime = mergeAndSum(e.getValue());
            double availability = windowSec > 0
                    ? Math.max(0, (windowSec - downtime) * 100.0 / windowSec) : 100.0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("monitorName", e.getKey());
            m.put("downtimeSec", downtime);
            m.put("availability", round(availability));
            monitors.add(m);
            worst = Math.min(worst, availability);
        }
        monitors.sort(Comparator.comparingDouble(m -> (double) m.get("availability")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("windowHours", hours <= 0 ? 24 : hours);
        result.put("monitorCountWithIncident", monitors.size());
        result.put("worstAvailability", monitors.isEmpty() ? 100.0 : round(worst));
        result.put("monitors", monitors);
        return result;
    }

    private long mergeAndSum(List<long[]> intervals) {
        intervals.sort(Comparator.comparingLong(a -> a[0]));
        long total = 0;
        long curStart = -1;
        long curEnd = -1;
        for (long[] it : intervals) {
            if (curEnd < it[0]) {
                if (curStart >= 0) {
                    total += curEnd - curStart;
                }
                curStart = it[0];
                curEnd = it[1];
            } else {
                curEnd = Math.max(curEnd, it[1]);
            }
        }
        if (curStart >= 0) {
            total += curEnd - curStart;
        }
        return total;
    }

    private long toEpoch(LocalDateTime t) {
        return t.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
    }

    private double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
