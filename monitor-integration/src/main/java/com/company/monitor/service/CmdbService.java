package com.company.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.monitor.common.BizException;
import com.company.monitor.entity.ServiceOwner;
import com.company.monitor.mapper.ServiceOwnerMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CMDB 对接：从公司 CMDB 拉取「服务 → 负责人」映射，同步进 service_owner。
 *
 * <p>期望 CMDB 返回 JSON 数组，元素含 serviceName / wecomUserids / emailList
 * （字段名可经 cmdb.field-* 适配）。各公司 CMDB 结构不同，可在此做适配层。
 */
@Slf4j
@Service
public class CmdbService {

    private final OkHttpClient httpClient;
    private final SsrfGuard ssrfGuard;
    private final ServiceOwnerMapper serviceOwnerMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CmdbService(OkHttpClient httpClient, SsrfGuard ssrfGuard, ServiceOwnerMapper serviceOwnerMapper) {
        this.httpClient = httpClient;
        this.ssrfGuard = ssrfGuard;
        this.serviceOwnerMapper = serviceOwnerMapper;
    }

    public Map<String, Object> syncFromUrl(String url, String svcField, String wecomField, String emailField) {
        ssrfGuard.validateFetchUrl(url);
        try {
            Request req = new Request.Builder().url(url).get().build();
            try (Response resp = httpClient.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    throw new BizException("CMDB 拉取失败 http=" + resp.code());
                }
                String body = resp.body() != null ? resp.body().string() : "";
                JsonNode root = objectMapper.readTree(body);
                // 兼容 {data:[...]} 或直接数组
                JsonNode arr = root.isArray() ? root : root.path("data");
                return syncNodes(arr, svcField, wecomField, emailField);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("CMDB 同步异常: " + e.getMessage());
        }
    }

    public Map<String, Object> syncNodes(JsonNode arr, String svcField, String wecomField, String emailField) {
        String sf = svcField != null ? svcField : "serviceName";
        String wf = wecomField != null ? wecomField : "wecomUserids";
        String ef = emailField != null ? emailField : "emailList";
        int created = 0, updated = 0, skipped = 0;
        List<String> services = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                String svc = text(n, sf);
                if (svc == null || svc.isBlank()) {
                    skipped++;
                    continue;
                }
                ServiceOwner existing = serviceOwnerMapper.selectOne(
                        new QueryWrapper<ServiceOwner>().eq("service_name", svc).last("limit 1"));
                if (existing != null) {
                    existing.setWecomUserids(text(n, wf));
                    existing.setEmailList(text(n, ef));
                    serviceOwnerMapper.updateById(existing);
                    updated++;
                } else {
                    ServiceOwner so = new ServiceOwner();
                    so.setServiceName(svc);
                    so.setWecomUserids(text(n, wf));
                    so.setEmailList(text(n, ef));
                    so.setEnabled(1);
                    serviceOwnerMapper.insert(so);
                    created++;
                }
                services.add(svc);
            }
        }
        Map<String, Object> r = new HashMap<>();
        r.put("created", created);
        r.put("updated", updated);
        r.put("skipped", skipped);
        r.put("services", services);
        return r;
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
