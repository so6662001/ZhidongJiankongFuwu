package com.company.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.monitor.entity.Alert;
import com.company.monitor.entity.MonitorRef;
import com.company.monitor.mapper.AlertMapper;
import com.company.monitor.mapper.MonitorRefMapper;
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
    private final MonitorRefMapper monitorRefMapper;

    public SlaService(AlertMapper alertMapper, MonitorRefMapper monitorRefMapper) {
        this.alertMapper = alertMapper;
        this.monitorRefMapper = monitorRefMapper;
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

    /**
     * SLA 可用率趋势：按时间桶计算整体可用率（基于告警故障时段，按监控合并区间）。
     * 整体可用率 = (桶时长 * 监控总数 - 桶内故障时长) / (桶时长 * 监控总数)。
     */
    public Map<String, Object> computeSlaTrend(String serviceName, int hours, int buckets) {
        int h = hours <= 0 ? 24 : hours;
        int n = buckets <= 0 ? 24 : Math.min(buckets, 200);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(h);
        long fromEpoch = toEpoch(from), toEpochV = toEpoch(to);
        long span = toEpochV - fromEpoch;
        long bucketSec = Math.max(1, span / n);

        // 与窗口交叠的告警
        QueryWrapper<Alert> qw = new QueryWrapper<>();
        if (serviceName != null && !serviceName.isBlank()) {
            qw.eq("service_name", serviceName);
        }
        qw.le("first_seen", to);
        qw.and(w -> w.isNull("recovered_at").or().ge("recovered_at", from));
        List<Alert> alerts = alertMapper.selectList(qw);

        // 每监控合并故障区间
        Map<String, List<long[]>> byMonitor = new LinkedHashMap<>();
        for (Alert a : alerts) {
            LocalDateTime s = a.getFirstSeen() == null ? from : a.getFirstSeen();
            LocalDateTime e = a.getRecoveredAt() == null ? to : a.getRecoveredAt();
            if (s.isBefore(from)) s = from;
            if (e.isAfter(to)) e = to;
            if (!e.isAfter(s)) continue;
            String key = a.getMonitorName() != null ? a.getMonitorName() : ("m#" + a.getHzbMonitorId());
            byMonitor.computeIfAbsent(key, k -> new ArrayList<>()).add(new long[]{toEpoch(s), toEpoch(e)});
        }
        Map<String, List<long[]>> merged = new LinkedHashMap<>();
        for (Map.Entry<String, List<long[]>> en : byMonitor.entrySet()) {
            merged.put(en.getKey(), mergeIntervals(en.getValue()));
        }

        // 监控总数（分母）
        QueryWrapper<MonitorRef> mq = new QueryWrapper<>();
        if (serviceName != null && !serviceName.isBlank()) {
            mq.eq("service_name", serviceName);
        }
        long totalMonitors = monitorRefMapper.selectCount(mq);
        if (totalMonitors <= 0) {
            totalMonitors = Math.max(1, merged.size());
        }

        List<Map<String, Object>> points = new ArrayList<>();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm");
        double worst = 100.0;
        for (int i = 0; i < n; i++) {
            long bStart = fromEpoch + i * bucketSec;
            long bEnd = (i == n - 1) ? toEpochV : bStart + bucketSec;
            long bucketLen = Math.max(1, bEnd - bStart);
            long downtime = 0;
            for (List<long[]> ivs : merged.values()) {
                for (long[] iv : ivs) {
                    long ov = Math.min(iv[1], bEnd) - Math.max(iv[0], bStart);
                    if (ov > 0) downtime += ov;
                }
            }
            double avail = (bucketLen * totalMonitors - downtime) * 100.0 / (bucketLen * totalMonitors);
            avail = Math.max(0, Math.min(100, avail));
            worst = Math.min(worst, avail);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("time", from.plusSeconds(i * bucketSec).format(fmt));
            p.put("availability", round(avail));
            points.add(p);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowHours", h);
        result.put("totalMonitors", totalMonitors);
        result.put("worstBucketAvailability", round(worst));
        result.put("points", points);
        return result;
    }

    private List<long[]> mergeIntervals(List<long[]> intervals) {
        intervals.sort(Comparator.comparingLong(a -> a[0]));
        List<long[]> out = new ArrayList<>();
        long cs = -1, ce = -1;
        for (long[] it : intervals) {
            if (ce < it[0]) {
                if (cs >= 0) out.add(new long[]{cs, ce});
                cs = it[0];
                ce = it[1];
            } else {
                ce = Math.max(ce, it[1]);
            }
        }
        if (cs >= 0) out.add(new long[]{cs, ce});
        return out;
    }

    /**
     * 告警趋势：按时间桶统计新增告警数（基于 created_at）。
     */
    public Map<String, Object> computeTrend(int hours, int buckets) {
        int h = hours <= 0 ? 24 : hours;
        int n = buckets <= 0 ? 24 : Math.min(buckets, 200);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(h);
        long fromEpoch = toEpoch(from);
        long span = toEpoch(to) - fromEpoch;
        long bucketSec = Math.max(1, span / n);

        QueryWrapper<Alert> qw = new QueryWrapper<>();
        qw.ge("created_at", from);
        List<Alert> alerts = alertMapper.selectList(qw);

        long[] counts = new long[n];
        for (Alert a : alerts) {
            if (a.getCreatedAt() == null) {
                continue;
            }
            long idx = (toEpoch(a.getCreatedAt()) - fromEpoch) / bucketSec;
            if (idx >= 0 && idx < n) {
                counts[(int) idx]++;
            }
        }
        List<Map<String, Object>> points = new ArrayList<>();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm");
        for (int i = 0; i < n; i++) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("time", from.plusSeconds(i * bucketSec).format(fmt));
            p.put("count", counts[i]);
            points.add(p);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowHours", h);
        result.put("buckets", n);
        result.put("total", alerts.size());
        result.put("points", points);
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
