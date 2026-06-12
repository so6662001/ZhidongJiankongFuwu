package com.company.monitor.notify;

import com.company.monitor.config.IntegrationProperties;
import com.company.monitor.entity.Alert;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer(new IntegrationProperties());

    private Alert sampleAlert() {
        Alert a = new Alert();
        a.setServiceName("order-service");
        a.setMonitorName("order-service health");
        a.setUrl("https://example.com/api/health");
        a.setSeverity("P0");
        a.setContent("HTTP 500");
        a.setFirstSeen(LocalDateTime.now());
        a.setDurationSec(125);
        return a;
    }

    @Test
    void title_firing_containsSeverityAndService() {
        String t = renderer.title(sampleAlert(), false);
        assertTrue(t.contains("P0"));
        assertTrue(t.contains("order-service"));
        assertTrue(t.contains("告警"));
    }

    @Test
    void title_recovered_marksRecovered() {
        String t = renderer.title(sampleAlert(), true);
        assertTrue(t.contains("已恢复"));
    }

    @Test
    void emailHtml_firing_includesErrorAndUrl() {
        String html = renderer.emailHtml(sampleAlert(), false);
        assertTrue(html.contains("HTTP 500"));
        assertTrue(html.contains("https://example.com/api/health"));
        assertTrue(html.contains("告警中"));
    }

    @Test
    void wecomDescription_recovered_includesDuration() {
        String desc = renderer.wecomDescription(sampleAlert(), true);
        // 125s -> 2分5秒
        assertTrue(desc.contains("2分5秒"), "持续时长格式化: " + desc);
        assertTrue(desc.contains("已恢复"));
    }
}
