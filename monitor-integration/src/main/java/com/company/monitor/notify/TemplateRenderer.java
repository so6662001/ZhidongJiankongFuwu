package com.company.monitor.notify;

import com.company.monitor.config.IntegrationProperties;
import com.company.monitor.entity.Alert;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * 告警/恢复通知文案渲染。
 */
@Component
public class TemplateRenderer {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IntegrationProperties properties;

    public TemplateRenderer(IntegrationProperties properties) {
        this.properties = properties;
    }

    public String title(Alert alert, boolean recovered) {
        if (recovered) {
            return "【API已恢复】" + safe(alert.getServiceName()) + " " + safe(alert.getMonitorName());
        }
        return "【API告警·" + safe(alert.getSeverity()) + "】" + safe(alert.getServiceName()) + " " + safe(alert.getMonitorName());
    }

    /** 企业微信 textcard description（支持简单 HTML 着色）。 */
    public String wecomDescription(Alert alert, boolean recovered) {
        StringBuilder sb = new StringBuilder();
        if (recovered) {
            sb.append("接口已恢复正常\n");
            sb.append("服务：").append(safe(alert.getServiceName())).append("\n");
            sb.append("监控：").append(safe(alert.getMonitorName())).append("\n");
            if (alert.getUrl() != null) {
                sb.append("接口：").append(alert.getUrl()).append("\n");
            }
            sb.append("故障持续：").append(durationText(alert)).append("\n");
            sb.append("恢复时间：").append(now());
        } else {
            sb.append("<div class=\"highlight\">服务：").append(safe(alert.getServiceName())).append("</div>");
            sb.append("监控：").append(safe(alert.getMonitorName())).append("\n");
            if (alert.getUrl() != null) {
                sb.append("接口：").append(alert.getUrl()).append("\n");
            }
            sb.append("等级：").append(safe(alert.getSeverity())).append("\n");
            sb.append("错误：").append(safe(alert.getContent())).append("\n");
            sb.append("首次发现：").append(alert.getFirstSeen() != null ? alert.getFirstSeen().format(FMT) : now());
        }
        return sb.toString();
    }

    public String emailHtml(Alert alert, boolean recovered) {
        String color = recovered ? "#52c41a" : severityColor(alert.getSeverity());
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,sans-serif;\">");
        sb.append("<h3 style=\"color:").append(color).append(";\">").append(title(alert, recovered)).append("</h3>");
        sb.append("<table style=\"border-collapse:collapse;\">");
        row(sb, "服务", safe(alert.getServiceName()));
        row(sb, "监控项", safe(alert.getMonitorName()));
        if (alert.getUrl() != null) {
            row(sb, "接口", alert.getUrl());
        }
        row(sb, "等级", safe(alert.getSeverity()));
        row(sb, "状态", recovered ? "已恢复" : "告警中");
        if (!recovered) {
            row(sb, "错误", safe(alert.getContent()));
            row(sb, "首次发现", alert.getFirstSeen() != null ? alert.getFirstSeen().format(FMT) : now());
        } else {
            row(sb, "故障持续", durationText(alert));
            row(sb, "恢复时间", now());
        }
        sb.append("</table>");
        sb.append("<p><a href=\"").append(detailUrl(alert)).append("\">查看详情</a></p>");
        sb.append("</div>");
        return sb.toString();
    }

    public String detailUrl(Alert alert) {
        return properties.getDetailBaseUrl();
    }

    private void row(StringBuilder sb, String k, String v) {
        sb.append("<tr><td style=\"padding:4px 12px 4px 0;color:#888;\">").append(k)
                .append("</td><td style=\"padding:4px 0;\">").append(v).append("</td></tr>");
    }

    private String severityColor(String severity) {
        if (severity == null) {
            return "#fa8c16";
        }
        return switch (severity.toUpperCase()) {
            case "P0", "CRITICAL" -> "#f5222d";
            case "P1", "WARNING" -> "#fa8c16";
            default -> "#1890ff";
        };
    }

    private String durationText(Alert alert) {
        if (alert.getDurationSec() == null) {
            return "-";
        }
        long s = alert.getDurationSec();
        if (s < 60) {
            return s + "秒";
        }
        if (s < 3600) {
            return (s / 60) + "分" + (s % 60) + "秒";
        }
        return (s / 3600) + "小时" + ((s % 3600) / 60) + "分";
    }

    private String now() {
        return java.time.LocalDateTime.now().format(FMT);
    }

    private String safe(String s) {
        return s == null ? "-" : s;
    }
}
