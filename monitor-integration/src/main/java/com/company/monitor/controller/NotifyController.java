package com.company.monitor.controller;

import com.company.monitor.common.Result;
import com.company.monitor.entity.Alert;
import com.company.monitor.notify.NotificationGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "通知联调")
@RestController
@RequestMapping("/api/v1/notify")
public class NotifyController {

    private final NotificationGateway gateway;

    public NotifyController(NotificationGateway gateway) {
        this.gateway = gateway;
    }

    @Data
    public static class TestRequest {
        /** 按服务负责人路由测试；与 wecomUserids/emails 二选一 */
        private String serviceName;
        /** 直接指定企业微信 userid（逗号分隔） */
        private String wecomUserids;
        /** 直接指定邮件接收人（逗号分隔） */
        private String emails;
    }

    @Operation(summary = "发送测试通知（验证企业微信/邮件投递与兜底切换）")
    @PostMapping("/test")
    public Result<Map<String, Object>> test(@RequestBody TestRequest req) {
        Alert sample = new Alert();
        sample.setId(0L); // 测试样例，notify_log.alert_id 记 0
        sample.setServiceName(req.getServiceName() != null ? req.getServiceName() : "test-service");
        sample.setMonitorName("通知联调测试");
        sample.setUrl("https://example.com/api/health");
        sample.setSeverity("P1");
        sample.setStatus("firing");
        sample.setContent("这是一条测试通知，用于验证渠道配置与兜底切换。");
        sample.setFirstSeen(LocalDateTime.now());

        List<String> wecom;
        List<String> emails;
        if (req.getServiceName() != null && req.getWecomUserids() == null && req.getEmails() == null) {
            wecom = gateway.wecomUserIdsForService(req.getServiceName());
            emails = gateway.emailsForService(req.getServiceName());
        } else {
            wecom = gateway.splitList(req.getWecomUserids());
            emails = gateway.splitList(req.getEmails());
        }
        gateway.notifyToReceivers(sample, false, wecom, emails);
        return Result.ok(Map.of(
                "sentToWecom", wecom,
                "sentToEmail", emails,
                "note", "请查看 /api/v1/alerts 无关；通知回执见日志与 notify_log（按真实告警记录）"));
    }
}
