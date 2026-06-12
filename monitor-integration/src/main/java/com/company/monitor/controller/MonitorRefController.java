package com.company.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.monitor.common.BizException;
import com.company.monitor.common.Result;
import com.company.monitor.dto.MonitorRefUpdateRequest;
import com.company.monitor.entity.MonitorRef;
import com.company.monitor.mapper.MonitorRefMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "本地监控项管理")
@RestController
@RequestMapping("/api/v1/monitors")
public class MonitorRefController {

    private final MonitorRefMapper monitorRefMapper;

    public MonitorRefController(MonitorRefMapper monitorRefMapper) {
        this.monitorRefMapper = monitorRefMapper;
    }

    @Operation(summary = "查询本地登记的监控项（分页/过滤）")
    @GetMapping
    public Result<Page<MonitorRef>> list(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "20") long size,
                                         @RequestParam(required = false) String serviceName,
                                         @RequestParam(required = false) String severity,
                                         @RequestParam(required = false) String keyword) {
        QueryWrapper<MonitorRef> qw = new QueryWrapper<>();
        if (serviceName != null && !serviceName.isBlank()) {
            qw.eq("service_name", serviceName);
        }
        if (severity != null && !severity.isBlank()) {
            qw.eq("severity", severity);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("name", keyword).or().like("url", keyword));
        }
        qw.orderByDesc("id");
        return Result.ok(monitorRefMapper.selectPage(new Page<>(page, size), qw));
    }

    @Operation(summary = "更新本地监控项业务属性（服务/等级/负责人/启停）")
    @PutMapping("/{id}")
    public Result<MonitorRef> update(@PathVariable Long id, @RequestBody MonitorRefUpdateRequest req) {
        MonitorRef ref = monitorRefMapper.selectById(id);
        if (ref == null) {
            throw new BizException(404, "监控项不存在: " + id);
        }
        if (req.getServiceName() != null) {
            ref.setServiceName(req.getServiceName());
        }
        if (req.getSeverity() != null) {
            ref.setSeverity(req.getSeverity());
        }
        if (req.getOwner() != null) {
            ref.setOwner(req.getOwner());
        }
        if (req.getEnabled() != null) {
            ref.setEnabled(req.getEnabled());
        }
        monitorRefMapper.updateById(ref);
        return Result.ok(ref);
    }
}
