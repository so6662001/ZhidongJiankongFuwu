package com.company.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.monitor.common.BizException;
import com.company.monitor.common.Result;
import com.company.monitor.entity.Alert;
import com.company.monitor.entity.NotifyLog;
import com.company.monitor.mapper.AlertMapper;
import com.company.monitor.mapper.NotifyLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "告警查询与统计")
@RestController
@RequestMapping("/api/v1")
public class AlertController {

    private final AlertMapper alertMapper;
    private final NotifyLogMapper notifyLogMapper;
    private final com.company.monitor.service.SlaService slaService;

    public AlertController(AlertMapper alertMapper, NotifyLogMapper notifyLogMapper,
                           com.company.monitor.service.SlaService slaService) {
        this.alertMapper = alertMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.slaService = slaService;
    }

    @Operation(summary = "告警列表（分页/过滤）")
    @GetMapping("/alerts")
    public Result<Page<Alert>> list(@RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "20") long size,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String severity,
                                    @RequestParam(required = false) String serviceName) {
        QueryWrapper<Alert> qw = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq("status", status);
        }
        if (severity != null && !severity.isBlank()) {
            qw.eq("severity", severity);
        }
        if (serviceName != null && !serviceName.isBlank()) {
            qw.eq("service_name", serviceName);
        }
        qw.orderByDesc("id");
        return Result.ok(alertMapper.selectPage(new Page<>(page, size), qw));
    }

    @Operation(summary = "告警详情（含通知回执）")
    @GetMapping("/alerts/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Alert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new BizException(404, "告警不存在: " + id);
        }
        List<NotifyLog> logs = notifyLogMapper.selectList(
                new QueryWrapper<NotifyLog>().eq("alert_id", id).orderByDesc("id"));
        Map<String, Object> data = new HashMap<>();
        data.put("alert", alert);
        data.put("notifyLogs", logs);
        return Result.ok(data);
    }

    @Operation(summary = "概览统计（当前 firing、今日告警、按服务分布）")
    @GetMapping("/stats/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        Long firing = alertMapper.selectCount(new QueryWrapper<Alert>().eq("status", "firing"));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        Long today = alertMapper.selectCount(new QueryWrapper<Alert>().ge("created_at", todayStart));
        List<Map<String, Object>> byService = alertMapper.selectMaps(new QueryWrapper<Alert>()
                        .select("service_name", "count(*) as cnt")
                        .eq("status", "firing")
                        .groupBy("service_name"))
                .stream().map(m -> {
                    Map<String, Object> x = new HashMap<>();
                    x.put("serviceName", m.get("service_name"));
                    x.put("count", m.get("cnt"));
                    return x;
                }).toList();
        data.put("firing", firing);
        data.put("today", today);
        data.put("firingByService", byService);
        return Result.ok(data);
    }

    @Operation(summary = "SLA/可用率（按监控合并故障时段估算）")
    @GetMapping("/stats/sla")
    public Result<Map<String, Object>> sla(@RequestParam(required = false) String serviceName,
                                           @RequestParam(defaultValue = "24") int hours) {
        return Result.ok(slaService.computeSla(serviceName, hours));
    }

    @Operation(summary = "告警趋势（按时间桶统计新增告警数）")
    @GetMapping("/stats/trend")
    public Result<Map<String, Object>> trend(@RequestParam(defaultValue = "24") int hours,
                                             @RequestParam(defaultValue = "24") int buckets) {
        return Result.ok(slaService.computeTrend(hours, buckets));
    }

    @Operation(summary = "SLA 可用率趋势（按时间桶整体可用率）")
    @GetMapping("/stats/sla-trend")
    public Result<Map<String, Object>> slaTrend(@RequestParam(required = false) String serviceName,
                                                @RequestParam(defaultValue = "24") int hours,
                                                @RequestParam(defaultValue = "24") int buckets) {
        return Result.ok(slaService.computeSlaTrend(serviceName, hours, buckets));
    }

    @Data
    public static class AckRequest {
        private String ackedBy;
        private String remark;
    }

    @Operation(summary = "认领/确认告警（记录处理人与备注）")
    @PostMapping("/alerts/{id}/ack")
    public Result<Alert> ack(@PathVariable Long id, @RequestBody(required = false) AckRequest req) {
        Alert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new BizException(404, "告警不存在: " + id);
        }
        alert.setAckedBy(req != null && req.getAckedBy() != null ? req.getAckedBy() : "unknown");
        alert.setAckedAt(LocalDateTime.now());
        if (req != null && req.getRemark() != null) {
            alert.setRemark(req.getRemark());
        }
        alertMapper.updateById(alert);
        return Result.ok(alert);
    }
}
