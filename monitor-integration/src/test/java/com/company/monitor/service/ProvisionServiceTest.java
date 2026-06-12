package com.company.monitor.service;

import com.company.monitor.dto.ImportItem;
import com.company.monitor.dto.ImportRequest;
import com.company.monitor.dto.ImportResult;
import com.company.monitor.dto.OpenApiImportRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAPI 解析与清单导入（dryRun 预览，不触达 DB / HertzBeat）。
 */
class ProvisionServiceTest {

    // dryRun 分支不会调用 HertzBeat 客户端、Mapper、SsrfGuard 或 properties，可传 null
    private final ProvisionService service = new ProvisionService(null, null, null, null, null);

    private static final String OPENAPI = """
            {
              "openapi": "3.0.1",
              "info": {"title": "order-service", "version": "1.0.0"},
              "servers": [{"url": "https://example.com/api"}],
              "paths": {
                "/health": {"get": {"operationId": "health", "responses": {"200": {"description": "ok"}}}},
                "/orders": {"get": {"operationId": "listOrders", "responses": {"200": {"description": "ok"}}}},
                "/orders/{id}": {"get": {"operationId": "getOrder", "responses": {"200": {"description": "ok"}}}}
              }
            }
            """;

    @Test
    void importOpenApi_dryRun_parsesAllGetOperations() {
        OpenApiImportRequest req = new OpenApiImportRequest();
        req.setOpenapiContent(OPENAPI);
        req.setDryRun(true);
        req.setSeverity("P1");

        ImportResult result = service.importOpenApi(req);

        assertTrue(result.isDryRun());
        assertEquals(3, result.getTotal());
        assertEquals(0, result.getCreated());
        assertEquals(3, result.getDetails().size());
        assertTrue(result.getDetails().stream().allMatch(i -> "preview".equals(i.getAction())));
    }

    @Test
    void importOpenApi_flagsPathParametersForReview() {
        OpenApiImportRequest req = new OpenApiImportRequest();
        req.setOpenapiContent(OPENAPI);
        req.setDryRun(true);

        ImportResult result = service.importOpenApi(req);

        ImportResult.Item withParam = result.getDetails().stream()
                .filter(i -> i.getUrl() != null && i.getUrl().contains("/orders/"))
                .findFirst().orElseThrow();
        assertTrue(withParam.isNeedsReview(), "含路径参数的接口应标记 needsReview");
        assertTrue(withParam.getUrl().endsWith("/orders/1"), "路径参数应被占位替换");

        ImportResult.Item health = result.getDetails().stream()
                .filter(i -> i.getUrl() != null && i.getUrl().endsWith("/api/health"))
                .findFirst().orElseThrow();
        assertFalse(health.isNeedsReview());
    }

    @Test
    void importItems_dryRun_buildsSpecFromUrl() {
        ImportItem item = new ImportItem();
        item.setUrl("https://pay.example.com:8443/pay/ping");
        item.setMethod("get");
        ImportRequest req = new ImportRequest();
        req.setItems(List.of(item));
        req.setDryRun(true);
        req.setDefaultServiceName("pay-svc");

        ImportResult result = service.importItems(req);

        assertEquals(1, result.getTotal());
        ImportResult.Item it = result.getDetails().get(0);
        assertEquals("preview", it.getAction());
        assertEquals("pay-svc", it.getServiceName());
        assertEquals("https://pay.example.com:8443/pay/ping", it.getUrl());
    }
}
