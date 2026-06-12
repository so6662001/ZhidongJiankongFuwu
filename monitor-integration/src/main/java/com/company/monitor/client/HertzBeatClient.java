package com.company.monitor.client;

import com.company.monitor.common.BizException;
import com.company.monitor.config.HertzBeatProperties;
import com.company.monitor.dto.ApiMonitorSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HertzBeat REST API 客户端。
 *
 * <p>说明：HertzBeat 不同版本的接口路径/字段可能存在差异，本客户端实现以常见版本为准，
 * 接入前请对照部署版本的 Swagger/Knife4j 文档（通常在 /swagger-ui 或 /doc.html）核对。
 */
@Slf4j
@Component
public class HertzBeatClient {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final HertzBeatProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 缓存的访问令牌；失效时重新登录。 */
    private final AtomicReference<String> tokenRef = new AtomicReference<>();

    public HertzBeatClient(OkHttpClient httpClient, HertzBeatProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    /**
     * 登录并缓存 token。
     */
    public synchronized String login() {
        String url = base() + "/api/account/auth/form";
        try {
            String body = objectMapper.createObjectNode()
                    .put("identifier", properties.getUsername())
                    .put("credential", properties.getPassword())
                    .toString();
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(body, JSON))
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    throw new BizException("HertzBeat 登录失败, http=" + response.code());
                }
                JsonNode root = objectMapper.readTree(respBody);
                JsonNode token = root.path("data").path("token");
                if (token.isMissingNode() || token.asText().isEmpty()) {
                    throw new BizException("HertzBeat 登录响应缺少 token: " + respBody);
                }
                tokenRef.set(token.asText());
                log.info("HertzBeat 登录成功");
                return tokenRef.get();
            }
        } catch (IOException e) {
            throw new BizException("HertzBeat 登录请求异常: " + e.getMessage());
        }
    }

    private String token() {
        String t = tokenRef.get();
        return t != null ? t : login();
    }

    /**
     * 查询监控项列表（原样返回 HertzBeat 响应体）。
     *
     * @param query 可选的查询关键字
     */
    public JsonNode listMonitors(String query) {
        StringBuilder url = new StringBuilder(base()).append("/api/monitors?pageIndex=0&pageSize=20");
        if (query != null && !query.isBlank()) {
            url.append("&search=").append(query);
        }
        return authedGet(url.toString());
    }

    /**
     * 新增 HTTP API 监控，返回新建监控的 HertzBeat ID。
     */
    public Long addMonitor(ApiMonitorSpec spec) {
        ObjectNode body = buildMonitorDto(spec, null, Map.of());
        JsonNode resp = authedPost(base() + "/api/monitor", body);
        checkCode(resp, "新增监控");
        // HertzBeat 新增不返回 ID，按名称回查
        Long id = findMonitorIdByName(spec.getName());
        if (id == null) {
            throw new BizException("新增监控成功但回查 ID 失败: " + spec.getName());
        }
        return id;
    }

    /**
     * 更新已有监控。需携带既有 param 的 id，否则 HertzBeat 会重复插入触发唯一键冲突。
     */
    public void updateMonitor(Long hzbMonitorId, ApiMonitorSpec spec) {
        Map<String, Long> existingParamIds = getExistingParamIds(hzbMonitorId);
        ObjectNode body = buildMonitorDto(spec, hzbMonitorId, existingParamIds);
        JsonNode resp = authedPut(base() + "/api/monitor", body);
        checkCode(resp, "更新监控");
    }

    /**
     * 查询监控既有参数的 field -> paramId 映射。
     */
    private Map<String, Long> getExistingParamIds(Long hzbMonitorId) {
        JsonNode root = authedGet(base() + "/api/monitor/" + hzbMonitorId);
        Map<String, Long> map = new java.util.HashMap<>();
        JsonNode params = root.path("data").path("params");
        if (params.isArray()) {
            for (JsonNode p : params) {
                if (p.hasNonNull("field") && p.hasNonNull("id")) {
                    map.put(p.get("field").asText(), p.get("id").asLong());
                }
            }
        }
        return map;
    }

    /**
     * 删除监控。
     */
    public void deleteMonitor(Long hzbMonitorId) {
        JsonNode resp = authedDelete(base() + "/api/monitor/" + hzbMonitorId);
        checkCode(resp, "删除监控");
    }

    /**
     * 按名称精确查找监控 ID，找不到返回 null。
     */
    public Long findMonitorIdByName(String name) {
        JsonNode root = authedGet(base() + "/api/monitors?pageIndex=0&pageSize=50&sort=gmtCreate&order=desc");
        JsonNode content = root.path("data").path("content");
        if (content.isArray()) {
            for (JsonNode m : content) {
                if (name.equals(m.path("name").asText())) {
                    return m.path("id").asLong();
                }
            }
        }
        return null;
    }

    /**
     * 把规范化规格映射为 HertzBeat MonitorDto（app=api）。
     */
    private ObjectNode buildMonitorDto(ApiMonitorSpec spec, Long monitorId, Map<String, Long> existingParamIds) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("detected", false);

        ObjectNode monitor = objectMapper.createObjectNode();
        if (monitorId != null) {
            monitor.put("id", monitorId);
        }
        monitor.put("name", spec.getName());
        monitor.put("app", "api");
        monitor.put("host", spec.getHost());
        // instance 必填非空：HertzBeat 更新时用其构建 job metadata（Map.of 不允许 null 值，否则 NPE）
        monitor.put("instance", spec.getHost());
        monitor.put("intervals", spec.getIntervalSec() != null ? spec.getIntervalSec() : 60);
        monitor.put("status", spec.isEnabled() ? 1 : 0);
        monitor.putArray("tags");
        monitor.put("description", buildDescription(spec));
        root.set("monitor", monitor);

        ArrayNode params = objectMapper.createArrayNode();
        int port = spec.getPort() != null ? spec.getPort() : ("https".equalsIgnoreCase(spec.getScheme()) ? 443 : 80);
        boolean ssl = "https".equalsIgnoreCase(spec.getScheme());
        addParam(params, existingParamIds, monitorId, "host", "1", spec.getHost());
        addParam(params, existingParamIds, monitorId, "port", "0", String.valueOf(port));
        addParam(params, existingParamIds, monitorId, "httpMethod", "1", spec.getMethod() != null ? spec.getMethod() : "GET");
        addParam(params, existingParamIds, monitorId, "uri", "1", spec.getUri() != null ? spec.getUri() : "");
        addParam(params, existingParamIds, monitorId, "ssl", "1", String.valueOf(ssl));
        addParam(params, existingParamIds, monitorId, "timeout", "0", String.valueOf(spec.getTimeoutMs() != null ? spec.getTimeoutMs() : 6000));
        addParam(params, existingParamIds, monitorId, "enableUrlEncoding", "1", "true");
        if (spec.getSuccessCodes() != null && !spec.getSuccessCodes().isEmpty()) {
            addParam(params, existingParamIds, monitorId, "successCode", "4", String.join(",", spec.getSuccessCodes()));
        }
        if (spec.getHeaders() != null && !spec.getHeaders().isEmpty()) {
            addParam(params, existingParamIds, monitorId, "headers", "3", toJson(spec.getHeaders()));
        }
        if (spec.getContentType() != null) {
            addParam(params, existingParamIds, monitorId, "contentType", "1", spec.getContentType());
        }
        if (spec.getBody() != null) {
            addParam(params, existingParamIds, monitorId, "payload", "1", spec.getBody());
        }
        root.set("params", params);
        return root;
    }

    private String buildDescription(ApiMonitorSpec spec) {
        StringBuilder sb = new StringBuilder();
        if (spec.getServiceName() != null) {
            sb.append("service=").append(spec.getServiceName()).append("; ");
        }
        if (spec.getSeverity() != null) {
            sb.append("severity=").append(spec.getSeverity()).append("; ");
        }
        if (spec.getFullUrl() != null) {
            sb.append(spec.getMethod()).append(" ").append(spec.getFullUrl());
        }
        return sb.toString();
    }

    private void addParam(ArrayNode params, Map<String, Long> existingParamIds, Long monitorId,
                          String field, String type, String value) {
        ObjectNode p = objectMapper.createObjectNode();
        Long paramId = existingParamIds != null ? existingParamIds.get(field) : null;
        if (paramId != null) {
            p.put("id", paramId);
        }
        if (monitorId != null) {
            p.put("monitorId", monitorId);
        }
        p.put("field", field);
        p.put("type", type);
        p.put("paramValue", value);
        params.add(p);
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (IOException e) {
            return "{}";
        }
    }

    private void checkCode(JsonNode resp, String action) {
        int code = resp.path("code").asInt(-1);
        if (code != 0) {
            throw new BizException(action + "失败: " + resp.path("msg").asText());
        }
    }

    private JsonNode authedPost(String url, JsonNode body) {
        return authedSend("POST", url, body);
    }

    private JsonNode authedPut(String url, JsonNode body) {
        return authedSend("PUT", url, body);
    }

    private JsonNode authedDelete(String url) {
        return authedSend("DELETE", url, null);
    }

    private JsonNode authedSend(String method, String url, JsonNode body) {
        try {
            JsonNode result = doSend(method, url, body, token());
            if (result == null) {
                login();
                result = doSend(method, url, body, token());
            }
            return result;
        } catch (IOException e) {
            throw new BizException("调用 HertzBeat 接口异常: " + e.getMessage());
        }
    }

    private JsonNode doSend(String method, String url, JsonNode body, String token) throws IOException {
        RequestBody reqBody = body != null
                ? RequestBody.create(body.toString(), JSON)
                : ("DELETE".equals(method) ? null : RequestBody.create("", JSON));
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .method(method, reqBody)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                return null;
            }
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new BizException("HertzBeat 接口返回错误, http=" + response.code() + ", body=" + respBody);
            }
            return objectMapper.readTree(respBody);
        }
    }

    private JsonNode authedGet(String url) {
        try {
            JsonNode result = doGet(url, token());
            if (result == null) {
                // token 可能失效，重新登录后重试一次
                login();
                result = doGet(url, token());
            }
            return result;
        } catch (IOException e) {
            throw new BizException("调用 HertzBeat 接口异常: " + e.getMessage());
        }
    }

    /**
     * @return null 表示鉴权失效（需重登），否则返回响应体
     */
    private JsonNode doGet(String url, String token) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 401 || response.code() == 403) {
                return null;
            }
            String respBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new BizException("HertzBeat 接口返回错误, http=" + response.code() + ", body=" + respBody);
            }
            return objectMapper.readTree(respBody);
        }
    }

    private String base() {
        String b = properties.getBaseUrl();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }
}
