package com.company.monitor.controller;

import com.company.monitor.common.BizException;
import com.company.monitor.common.Result;
import com.company.monitor.service.CmdbService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "CMDB 对接")
@RestController
@RequestMapping("/api/v1/cmdb")
public class CmdbController {

    private final CmdbService cmdbService;

    public CmdbController(CmdbService cmdbService) {
        this.cmdbService = cmdbService;
    }

    @Data
    public static class SyncRequest {
        /** CMDB 拉取地址（与 items 二选一） */
        private String url;
        /** 字段名适配（默认 serviceName/wecomUserids/emailList） */
        private String serviceField;
        private String wecomField;
        private String emailField;
        /** 直接内联服务负责人数组（与 url 二选一） */
        private JsonNode items;
    }

    @Operation(summary = "从 CMDB 同步服务负责人到 service_owner（支持 url 拉取或内联 items）")
    @PostMapping("/sync")
    public Result<Map<String, Object>> sync(@RequestBody SyncRequest req) {
        if (req.getItems() != null && req.getItems().isArray()) {
            return Result.ok(cmdbService.syncNodes(req.getItems(), req.getServiceField(), req.getWecomField(), req.getEmailField()));
        }
        if (req.getUrl() != null && !req.getUrl().isBlank()) {
            return Result.ok(cmdbService.syncFromUrl(req.getUrl(), req.getServiceField(), req.getWecomField(), req.getEmailField()));
        }
        throw new BizException("请提供 url 或 items");
    }
}
