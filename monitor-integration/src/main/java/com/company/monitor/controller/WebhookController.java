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
            @RequestHeader(value = "X-Webhook-Token", required = false) String headerToken,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody JsonNode payload) {
        // token 兼容两种来源：自定义头 X-Webhook-Token，或 HertzBeat webhook 的 Authorization: Bearer
        String token = headerToken;
        if ((token == null || token.isBlank()) && authorization != null
                && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = authorization.substring(7).trim();
        }
        // 鉴权：必须配置非空 token 且请求头匹配（fail-closed，防伪造调用）
        String serverToken = properties.getWebhookToken();
        if (serverToken == null || serverToken.isBlank()) {
            log.error("integration.webhook-token 未配置，拒绝 Webhook 请求（请配置 WEBHOOK_TOKEN）");
            return ResponseEntity.status(401).body(Result.error(401, "webhook token not configured"));
        }
        if (token == null || !java.security.MessageDigest.isEqual(
                serverToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
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
