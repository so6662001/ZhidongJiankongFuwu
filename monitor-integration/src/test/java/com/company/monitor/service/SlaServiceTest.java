package com.company.monitor.service;

import com.company.monitor.entity.Alert;
import com.company.monitor.mapper.AlertMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SlaServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void computeSla_mergesIntervalsAndComputesAvailability() {
        AlertMapper mapper = mock(AlertMapper.class);
        LocalDateTime now = LocalDateTime.now();

        Alert a1 = new Alert();
        a1.setMonitorName("m1");
        a1.setFirstSeen(now.minusHours(3));
        a1.setRecoveredAt(now.minusHours(3).plusSeconds(600)); // 600s

        // 与 a1 重叠的告警，合并后不应重复计时
        Alert a2 = new Alert();
        a2.setMonitorName("m1");
        a2.setFirstSeen(now.minusHours(3).plusSeconds(300));
        a2.setRecoveredAt(now.minusHours(3).plusSeconds(900)); // 区间 [300,900]，与 a1[0,600] 合并 => [0,900]=900s

        when(mapper.selectList(any())).thenReturn(List.of(a1, a2));

        SlaService service = new SlaService(mapper);
        Map<String, Object> result = service.computeSla(null, 24);

        List<Map<String, Object>> monitors = (List<Map<String, Object>>) result.get("monitors");
        assertEquals(1, monitors.size());
        Map<String, Object> m1 = monitors.get(0);
        assertEquals("m1", m1.get("monitorName"));
        assertEquals(900L, m1.get("downtimeSec"));  // 合并后 900s（非 600+600）
        double availability = (double) m1.get("availability");
        // (86400 - 900)/86400 * 100 ≈ 98.958
        assertTrue(availability > 98.9 && availability < 99.0, "availability=" + availability);
    }
}
