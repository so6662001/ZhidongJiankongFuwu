package com.company.monitor.controller;

import com.company.monitor.common.Result;
import com.company.monitor.dto.ImportRequest;
import com.company.monitor.dto.ImportResult;
import com.company.monitor.dto.OpenApiImportRequest;
import com.company.monitor.service.ProvisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "批量纳管 / OpenAPI 导入")
@RestController
@RequestMapping("/api/v1/provision")
public class ProvisionController {

    private final ProvisionService provisionService;

    public ProvisionController(ProvisionService provisionService) {
        this.provisionService = provisionService;
    }

    @Operation(summary = "OpenAPI 自动导入（解析文档批量生成监控项，默认 dryRun 预览）")
    @PostMapping("/import-openapi")
    public Result<ImportResult> importOpenApi(@RequestBody OpenApiImportRequest request) {
        return Result.ok(provisionService.importOpenApi(request));
    }

    @Operation(summary = "接口清单批量导入（JSON 数组）")
    @PostMapping("/import")
    public Result<ImportResult> importItems(@Valid @RequestBody ImportRequest request) {
        return Result.ok(provisionService.importItems(request));
    }
}
