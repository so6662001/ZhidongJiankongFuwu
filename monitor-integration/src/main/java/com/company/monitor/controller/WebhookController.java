package com.company.monitor.controller;

import com.company.monitor.common.Result;
import com.company.monitor.config.IntegrationProperties;
import com.company.monitor.service.AlertService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "HertzBeat 告警 Webhook")
@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {

    private final AlertService alertService;
    private final IntegrationProperties properties;

    public WebhookController(AlertService alertService, IntegrationProperties properties) {
        this.alertService = alertService;
        this.properties = properties;
    }

    @Operation(summary = "接收 HertzBeat 告警 Webhook（GroupAlert）")
    @PostMapping("/hertzbeat")
    public ResponseEntity<Result<Map<String, Object>>> hertzbeat(
            @RequestHeader(value = "X-Webhook-Token", required = false) String token,
            @RequestBody JsonNode payload) {
        // 鉴权：校验共享密钥，避免被伪造调用
        if (properties.getWebhookToken() != null && !properties.getWebhookToken().isBlank()
                && !properties.getWebhookToken().equals(token)) {
            log.warn("Webhook token 校验失败");
            return ResponseEntity.status(401).body(Result.error(401, "invalid token"));
        }
        int handled = 0;
        try {
            handled = alertService.handleWebhook(payload);
        } catch (Exception e) {
            // 异常也返回 200，避免 HertzBeat 反复重推；但记录日志
            log.error("处理告警 webhook 异常: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok(Result.ok(Map.of("handled", handled)));
    }
}
