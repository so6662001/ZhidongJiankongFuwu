package com.company.monitor.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.monitor.common.Result;
import com.company.monitor.entity.OncallSchedule;
import com.company.monitor.mapper.OncallScheduleMapper;
import com.company.monitor.service.OncallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "值班排班")
@RestController
@RequestMapping("/api/v1/oncall")
public class OncallController {

    private final OncallScheduleMapper mapper;
    private final OncallService oncallService;

    public OncallController(OncallScheduleMapper mapper, OncallService oncallService) {
        this.mapper = mapper;
        this.oncallService = oncallService;
    }

    @Operation(summary = "排班列表")
    @GetMapping("/schedules")
    public Result<List<OncallSchedule>> list() {
        return Result.ok(mapper.selectList(new QueryWrapper<OncallSchedule>().orderByDesc("start_at")));
    }

    @Operation(summary = "新增排班")
    @PostMapping("/schedules")
    public Result<OncallSchedule> create(@RequestBody OncallSchedule s) {
        if (s.getEnabled() == null) {
            s.setEnabled(1);
        }
        mapper.insert(s);
        return Result.ok(s);
    }

    @Operation(summary = "删除排班")
    @DeleteMapping("/schedules/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "查询当前当班接收人（可按服务）")
    @GetMapping("/current")
    public Result<Map<String, Object>> current(@RequestParam(required = false) String serviceName) {
        return Result.ok(Map.of(
                "wecomUserids", oncallService.currentWecomUserIds(serviceName),
                "emails", oncallService.currentEmails(serviceName)));
    }
}
