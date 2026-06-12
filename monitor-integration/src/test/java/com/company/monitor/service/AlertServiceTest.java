package com.company.monitor.service;

import com.company.monitor.config.IntegrationProperties;
import com.company.monitor.entity.Alert;
import com.company.monitor.mapper.AlertMapper;
import com.company.monitor.mapper.MonitorRefMapper;
import com.company.monitor.notify.NotificationGateway;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AlertServiceTest {

    private AlertMapper alertMapper;
    private MonitorRefMapper monitorRefMapper;
    private NotificationGateway gateway;
    private AlertService service;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        alertMapper = mock(AlertMapper.class);
        monitorRefMapper = mock(MonitorRefMapper.class);
        gateway = mock(NotificationGateway.class);
        service = new AlertService(alertMapper, monitorRefMapper, gateway, new IntegrationProperties(), null);
        when(monitorRefMapper.selectOne(any())).thenReturn(null);
    }

    private JsonNode payload(String status, boolean withEndAt) throws Exception {
        String end = withEndAt ? ",\"endAt\":\"2026-06-12 11:25:30\"" : "";
        String json = """
                {
                  "status": "%s",
                  "commonLabels": {"severity": "critical", "alertname": "http_code_abnormal"},
                  "alerts": [
                    {
                      "labels": {"alertname": "http_code_abnormal", "instancename": "order-service health", "instance": "example.com:443"},
                      "content": "HTTP 500",
                      "triggerTimes": 3,
                      "startAt": "2026-06-12 11:20:00"%s
                    }
                  ]
                }
                """.formatted(status, end);
        return om.readTree(json);
    }

    @Test
    void firing_createsAlertAndNotifies() throws Exception {
        when(alertMapper.selectOne(any())).thenReturn(null);

        int handled = service.handleWebhook(payload("firing", false));

        org.junit.jupiter.api.Assertions.assertEquals(1, handled);
        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertMapper, times(1)).insert(captor.capture());
        Alert created = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("firing", created.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("order-service health", created.getMonitorName());
        org.junit.jupiter.api.Assertions.assertEquals("P0", created.getSeverity());
        verify(gateway, times(1)).notify(any(Alert.class), eq(false));
    }

    @Test
    void resolved_updatesExistingFiringToRecovered() throws Exception {
        Alert firing = new Alert();
        firing.setId(99L);
        firing.setStatus("firing");
        firing.setFirstSeen(LocalDateTime.now().minusMinutes(5));
        when(alertMapper.selectOne(any())).thenReturn(firing);

        service.handleWebhook(payload("resolved", true));

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertMapper, atLeastOnce()).updateById(captor.capture());
        Alert updated = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("recovered", updated.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getRecoveredAt());
        org.junit.jupiter.api.Assertions.assertTrue(updated.getDurationSec() != null && updated.getDurationSec() > 0);
        verify(gateway, times(1)).notify(any(Alert.class), eq(true));
        verify(alertMapper, never()).insert(any(Alert.class));
    }
}
