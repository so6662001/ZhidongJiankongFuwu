package com.company.monitor.controller;

import com.company.monitor.client.HertzBeatClient;
import com.company.monitor.common.Result;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "HertzBeat 连通性")
@RestController
@RequestMapping("/api/v1/hertzbeat")
public class HertzBeatController {

    private final HertzBeatClient hertzBeatClient;

    public HertzBeatController(HertzBeatClient hertzBeatClient) {
        this.hertzBeatClient = hertzBeatClient;
    }

    @Operation(summary = "透传查询 HertzBeat 监控列表（验证集成层与引擎连通）")
    @GetMapping("/monitors")
    public Result<JsonNode> monitors(@RequestParam(required = false) String query) {
        return Result.ok(hertzBeatClient.listMonitors(query));
    }

    @Operation(summary = "查询监控响应时间历史（来自 HertzBeat 时序库）")
    @GetMapping("/monitors/{hzbMonitorId}/response-time")
    public Result<JsonNode> responseTime(@PathVariable long hzbMonitorId,
                                         @RequestParam(defaultValue = "6h") String history) {
        return Result.ok(hertzBeatClient.metricHistory(hzbMonitorId, "api.summary.responseTime", history));
    }
}
