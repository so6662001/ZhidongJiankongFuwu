package com.company.monitor.service;

import com.company.monitor.entity.Alert;
import com.company.monitor.entity.MaintenanceWindow;
import com.company.monitor.mapper.MaintenanceWindowMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SilenceServiceTest {

    private Alert alert(String service, String monitor) {
        Alert a = new Alert();
        a.setServiceName(service);
        a.setMonitorName(monitor);
        return a;
    }

    private MaintenanceWindow win(String type, String value) {
        MaintenanceWindow w = new MaintenanceWindow();
        w.setScopeType(type);
        w.setScopeValue(value);
        return w;
    }

    @Test
    void globalWindow_silencesAny() {
        MaintenanceWindowMapper m = mock(MaintenanceWindowMapper.class);
        when(m.selectList(any())).thenReturn(List.of(win("global", null)));
        assertTrue(new SilenceService(m).isSilenced(alert("svc", "mon")));
    }

    @Test
    void serviceWindow_matchesOnlyThatService() {
        MaintenanceWindowMapper m = mock(MaintenanceWindowMapper.class);
        when(m.selectList(any())).thenReturn(List.of(win("service", "order-service")));
        SilenceService s = new SilenceService(m);
        assertTrue(s.isSilenced(alert("order-service", "m1")));
        assertFalse(s.isSilenced(alert("pay-service", "m1")));
    }

    @Test
    void noActiveWindow_notSilenced() {
        MaintenanceWindowMapper m = mock(MaintenanceWindowMapper.class);
        when(m.selectList(any())).thenReturn(List.of());
        assertFalse(new SilenceService(m).isSilenced(alert("svc", "mon")));
    }
}
