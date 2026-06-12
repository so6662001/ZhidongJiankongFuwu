package com.company.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.monitor.common.Result;
import com.company.monitor.entity.MaintenanceWindow;
import com.company.monitor.mapper.MaintenanceWindowMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "维护窗口/静默")
@RestController
@RequestMapping("/api/v1/maintenance-windows")
public class MaintenanceController {

    private final MaintenanceWindowMapper mapper;

    public MaintenanceController(MaintenanceWindowMapper mapper) {
        this.mapper = mapper;
    }

    @Operation(summary = "维护窗口列表")
    @GetMapping
    public Result<List<MaintenanceWindow>> list() {
        return Result.ok(mapper.selectList(new QueryWrapper<MaintenanceWindow>().orderByDesc("id")));
    }

    @Operation(summary = "新增维护窗口（命中期间静默对应告警通知）")
    @PostMapping
    public Result<MaintenanceWindow> create(@RequestBody MaintenanceWindow w) {
        if (w.getScopeType() == null) {
            w.setScopeType("global");
        }
        if (w.getEnabled() == null) {
            w.setEnabled(1);
        }
        mapper.insert(w);
        return Result.ok(w);
    }

    @Operation(summary = "删除维护窗口")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return Result.ok();
    }
}
