package com.company.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.monitor.entity.OncallSchedule;
import com.company.monitor.mapper.OncallScheduleMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 值班排班：解析某服务当前当班的接收人（企业微信/邮件）。
 */
@Service
public class OncallService {

    private final OncallScheduleMapper mapper;

    public OncallService(OncallScheduleMapper mapper) {
        this.mapper = mapper;
    }

    /** 当前生效的排班（服务匹配 或 全局）。 */
    private List<OncallSchedule> currentShifts(String serviceName) {
        LocalDateTime now = LocalDateTime.now();
        return mapper.selectList(new QueryWrapper<OncallSchedule>()
                .eq("enabled", 1)
                .le("start_at", now)
                .ge("end_at", now)
                .and(w -> w.isNull("service_name").or().eq("service_name", serviceName == null ? "" : serviceName)));
    }

    public List<String> currentWecomUserIds(String serviceName) {
        List<String> r = new ArrayList<>();
        for (OncallSchedule s : currentShifts(serviceName)) {
            addSplit(r, s.getWecomUserids());
        }
        return r;
    }

    public List<String> currentEmails(String serviceName) {
        List<String> r = new ArrayList<>();
        for (OncallSchedule s : currentShifts(serviceName)) {
            addSplit(r, s.getEmailList());
        }
        return r;
    }

    private void addSplit(List<String> out, String s) {
        if (s == null || s.isBlank()) {
            return;
        }
        for (String x : s.split("[,;]")) {
            String v = x.trim();
            if (!v.isEmpty() && !out.contains(v)) {
                out.add(v);
            }
        }
    }
}
