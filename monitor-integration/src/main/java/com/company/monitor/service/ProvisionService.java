package com.company.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.company.monitor.client.HertzBeatClient;
import com.company.monitor.common.BizException;
import com.company.monitor.dto.*;
import com.company.monitor.entity.MonitorRef;
import com.company.monitor.mapper.MonitorRefMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class ProvisionService {

    private final HertzBeatClient hertzBeatClient;
    private final MonitorRefMapper monitorRefMapper;
    private final OkHttpClient httpClient;
    private final SsrfGuard ssrfGuard;
    private final com.company.monitor.config.IntegrationProperties properties;

    public ProvisionService(HertzBeatClient hertzBeatClient, MonitorRefMapper monitorRefMapper,
                            OkHttpClient httpClient, SsrfGuard ssrfGuard,
                            com.company.monitor.config.IntegrationProperties properties) {
        this.hertzBeatClient = hertzBeatClient;
        this.monitorRefMapper = monitorRefMapper;
        this.httpClient = httpClient;
        this.ssrfGuard = ssrfGuard;
        this.properties = properties;
    }

    /**
     * 公司接口清单批量导入。
     */
    public ImportResult importItems(ImportRequest req) {
        ImportResult result = new ImportResult();
        result.setDryRun(req.isDryRun());
        for (ImportItem item : req.getItems()) {
            try {
                ApiMonitorSpec spec = toSpec(item, req);
                applySpec(spec, req.isDryRun(), result);
            } catch (Exception e) {
                ApiMonitorSpec failed = new ApiMonitorSpec();
                failed.setName(item.getName());
                failed.setFullUrl(item.getUrl());
                failed.setServiceName(item.getServiceName());
                ImportResult.Item it = ImportResult.Item.of("failed", failed);
                it.setMessage(e.getMessage());
                result.add(it);
            }
        }
        ensureAlertRulesIfNeeded(req.isDryRun(), result);
        return result;
    }

    /**
     * OpenAPI 文档自动导入。
     */
    public ImportResult importOpenApi(OpenApiImportRequest req) {
        String content = req.getOpenapiContent();
        if ((content == null || content.isBlank()) && req.getOpenapiUrl() != null) {
            ssrfGuard.validateFetchUrl(req.getOpenapiUrl());
            content = fetch(req.getOpenapiUrl());
        }
        if (content == null || content.isBlank()) {
            throw new BizException("openapiContent 或 openapiUrl 至少提供一个");
        }

        SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(content, null, null);
        OpenAPI openAPI = parsed.getOpenAPI();
        if (openAPI == null) {
            throw new BizException("OpenAPI 解析失败: " + parsed.getMessages());
        }

        String serviceName = req.getServiceName();
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = openAPI.getInfo() != null && openAPI.getInfo().getTitle() != null
                    ? openAPI.getInfo().getTitle() : "openapi-import";
        }
        BaseUrl base = resolveBaseUrl(openAPI);

        Set<String> include = new HashSet<>();
        for (String m : (req.getIncludeMethods() != null ? req.getIncludeMethods() : List.of("GET"))) {
            include.add(m.toUpperCase(Locale.ROOT));
        }

        ImportResult result = new ImportResult();
        result.setDryRun(req.isDryRun());
        if (openAPI.getPaths() == null) {
            return result;
        }
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            String path = entry.getKey();
            PathItem pathItem = entry.getValue();
            for (Map.Entry<PathItem.HttpMethod, Operation> op : pathItem.readOperationsMap().entrySet()) {
                String method = op.getKey().name();
                if (!include.contains(method)) {
                    continue;
                }
                try {
                    ApiMonitorSpec spec = toSpec(serviceName, base, path, method, op.getValue(), req);
                    applySpec(spec, req.isDryRun(), result);
                } catch (Exception e) {
                    ApiMonitorSpec failed = new ApiMonitorSpec();
                    failed.setName(serviceName + " " + method + " " + path);
                    failed.setServiceName(serviceName);
                    ImportResult.Item it = ImportResult.Item.of("failed", failed);
                    it.setMessage(e.getMessage());
                    result.add(it);
                }
            }
        }
        ensureAlertRulesIfNeeded(req.isDryRun(), result);
        return result;
    }

    /**
     * 非 dryRun 且有监控被创建/更新时，确保默认告警规则存在（打通"立即通知"链路）。
     */
    private void ensureAlertRulesIfNeeded(boolean dryRun, ImportResult result) {
        if (dryRun) {
            return;
        }
        if (result.getCreated() > 0 || result.getUpdated() > 0) {
            try {
                hertzBeatClient.ensureDefaultApiAlertDefines();
            } catch (Exception e) {
                log.warn("确保默认告警规则失败: {}", e.getMessage());
            }
            String selfWebhook = properties.getSelfWebhookUrl();
            if (selfWebhook != null && !selfWebhook.isBlank()) {
                try {
                    hertzBeatClient.ensureWebhookNoticeRoute(selfWebhook, properties.getWebhookToken());
                } catch (Exception e) {
                    log.warn("确保 webhook 转发策略失败: {}", e.getMessage());
                }
            }
        }
    }

    // ----- 核心：把规格落地到 HertzBeat + 本地登记（幂等） -----

    private void applySpec(ApiMonitorSpec spec, boolean dryRun, ImportResult result) {
        if (dryRun) {
            result.add(ImportResult.Item.of("preview", spec));
            return;
        }
        MonitorRef existing = findExisting(spec.getServiceName(), spec.getName());
        if (existing != null && existing.getHzbMonitorId() != null) {
            hertzBeatClient.updateMonitor(existing.getHzbMonitorId(), spec);
            updateRef(existing, spec);
            ImportResult.Item it = ImportResult.Item.of("updated", spec);
            it.setHzbMonitorId(existing.getHzbMonitorId());
            result.add(it);
        } else {
            Long hzbId = hertzBeatClient.addMonitor(spec);
            MonitorRef ref = existing != null ? existing : new MonitorRef();
            ref.setHzbMonitorId(hzbId);
            fillRef(ref, spec);
            try {
                if (ref.getId() == null) {
                    monitorRefMapper.insert(ref);
                } else {
                    monitorRefMapper.updateById(ref);
                }
            } catch (Exception e) {
                // 本地落库失败 → 回滚已创建的 HertzBeat 监控，避免产生孤儿监控
                try {
                    hertzBeatClient.deleteMonitor(hzbId);
                    log.warn("本地登记失败，已回滚 HertzBeat 监控 id={}", hzbId);
                } catch (Exception ex) {
                    log.error("本地登记失败且回滚 HertzBeat 监控 id={} 也失败: {}", hzbId, ex.getMessage());
                }
                throw e;
            }
            ImportResult.Item it = ImportResult.Item.of("created", spec);
            it.setHzbMonitorId(hzbId);
            result.add(it);
        }
    }

    private MonitorRef findExisting(String serviceName, String name) {
        QueryWrapper<MonitorRef> qw = new QueryWrapper<>();
        qw.eq("name", name);
        if (serviceName != null) {
            qw.eq("service_name", serviceName);
        } else {
            qw.isNull("service_name");
        }
        return monitorRefMapper.selectOne(qw.last("limit 1"));
    }

    private void fillRef(MonitorRef ref, ApiMonitorSpec spec) {
        ref.setName(spec.getName());
        ref.setUrl(spec.getFullUrl());
        ref.setMethod(spec.getMethod());
        ref.setServiceName(spec.getServiceName());
        ref.setEnv(spec.getEnv());
        ref.setSeverity(spec.getSeverity());
        ref.setOwner(spec.getOwner());
        ref.setEnabled(spec.isEnabled() ? 1 : 0);
        ref.setSyncedAt(LocalDateTime.now());
    }

    private void updateRef(MonitorRef ref, ApiMonitorSpec spec) {
        fillRef(ref, spec);
        monitorRefMapper.updateById(ref);
    }

    // ----- 转换：清单项 → 规格 -----

    private ApiMonitorSpec toSpec(ImportItem item, ImportRequest req) {
        ApiMonitorSpec spec = new ApiMonitorSpec();
        applyUrl(spec, item.getUrl());
        spec.setMethod(item.getMethod() != null ? item.getMethod().toUpperCase(Locale.ROOT) : "GET");
        spec.setName(item.getName() != null && !item.getName().isBlank()
                ? item.getName()
                : defaultName(item.getServiceName() != null ? item.getServiceName() : req.getDefaultServiceName(), spec.getMethod(), spec.getUri()));
        spec.setServiceName(item.getServiceName() != null ? item.getServiceName() : req.getDefaultServiceName());
        spec.setEnv(item.getEnv() != null ? item.getEnv() : "prod");
        spec.setSeverity(item.getSeverity() != null ? item.getSeverity() : req.getDefaultSeverity());
        spec.setOwner(item.getOwner());
        spec.setIntervalSec(item.getIntervalSec() != null ? item.getIntervalSec() : req.getDefaultIntervalSec());
        if (item.getTimeoutMs() != null) {
            spec.setTimeoutMs(item.getTimeoutMs());
        }
        if (item.getHeaders() != null) {
            spec.setHeaders(item.getHeaders());
        }
        spec.setContentType(item.getContentType());
        spec.setBody(item.getBody());
        spec.setSuccessCodes(item.getSuccessCodes());
        spec.setCollector(item.getCollector() != null ? item.getCollector() : req.getDefaultCollector());
        return spec;
    }

    // ----- 转换：OpenAPI operation → 规格 -----

    private ApiMonitorSpec toSpec(String serviceName, BaseUrl base, String path, String method, Operation op, OpenApiImportRequest req) {
        ApiMonitorSpec spec = new ApiMonitorSpec();
        spec.setServiceName(serviceName);
        spec.setEnv("prod");
        spec.setSeverity(req.getSeverity() != null ? req.getSeverity() : "P2");
        spec.setIntervalSec(req.getIntervalSec() != null ? req.getIntervalSec() : 60);
        spec.setTimeoutMs(req.getTimeoutMs() != null ? req.getTimeoutMs() : 6000);
        spec.setMethod(method.toUpperCase(Locale.ROOT));
        spec.setScheme(base.scheme);
        spec.setHost(base.host);
        spec.setPort(base.port);
        spec.setCollector(req.getCollector());

        // 路径参数用占位值替换，标记需人工确认
        String resolvedPath = path;
        if (path.contains("{")) {
            resolvedPath = path.replaceAll("\\{[^}]+}", "1");
            spec.setNeedsReview(true);
            spec.setReviewNote("含路径参数，已用占位值替换，请人工确认: " + path);
        }
        String uri = joinUri(base.basePath, resolvedPath);
        spec.setUri(uri);
        spec.setFullUrl(base.scheme + "://" + base.host + (base.port != null ? ":" + base.port : "") + uri);

        String name = (op.getOperationId() != null && !op.getOperationId().isBlank())
                ? serviceName + " " + op.getOperationId()
                : defaultName(serviceName, spec.getMethod(), uri);
        spec.setName(truncate(name, 100));
        return spec;
    }

    // ----- 工具 -----

    private void applyUrl(ApiMonitorSpec spec, String url) {
        try {
            URI u = URI.create(url.trim());
            String scheme = u.getScheme() != null ? u.getScheme() : "https";
            spec.setScheme(scheme);
            spec.setHost(u.getHost());
            int port = u.getPort();
            spec.setPort(port > 0 ? port : ("https".equalsIgnoreCase(scheme) ? 443 : 80));
            String uri = u.getRawPath() != null ? u.getRawPath() : "/";
            if (u.getRawQuery() != null) {
                uri = uri + "?" + u.getRawQuery();
            }
            spec.setUri(uri);
            spec.setFullUrl(url);
            if (spec.getHost() == null) {
                throw new BizException("URL 缺少 host: " + url);
            }
        } catch (IllegalArgumentException e) {
            throw new BizException("非法 URL: " + url);
        }
    }

    private BaseUrl resolveBaseUrl(OpenAPI openAPI) {
        BaseUrl base = new BaseUrl();
        base.scheme = "https";
        base.basePath = "";
        if (openAPI.getServers() != null && !openAPI.getServers().isEmpty()) {
            Server server = openAPI.getServers().get(0);
            String raw = server.getUrl();
            if (raw != null && !raw.isBlank()) {
                if (raw.startsWith("/")) {
                    base.basePath = stripTrailingSlash(raw);
                } else {
                    URI u = URI.create(raw);
                    if (u.getScheme() != null) {
                        base.scheme = u.getScheme();
                    }
                    base.host = u.getHost();
                    if (u.getPort() > 0) {
                        base.port = u.getPort();
                    }
                    base.basePath = u.getPath() != null ? stripTrailingSlash(u.getPath()) : "";
                }
            }
        }
        if (base.host == null) {
            throw new BizException("OpenAPI 未提供可用的 servers.url（缺少 host），请在请求中或文档中补充");
        }
        if (base.port == null) {
            base.port = "https".equalsIgnoreCase(base.scheme) ? 443 : 80;
        }
        return base;
    }

    private String fetch(String url) {
        try {
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new BizException("拉取 OpenAPI 文档失败, http=" + response.code());
                }
                return response.body() != null ? response.body().string() : "";
            }
        } catch (Exception e) {
            throw new BizException("拉取 OpenAPI 文档异常: " + e.getMessage());
        }
    }

    private String joinUri(String basePath, String path) {
        String b = basePath == null ? "" : basePath;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return b + path;
    }

    private String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private String defaultName(String serviceName, String method, String uri) {
        String svc = serviceName != null ? serviceName : "api";
        return truncate(svc + " " + method + " " + uri, 100);
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static class BaseUrl {
        String scheme;
        String host;
        Integer port;
        String basePath;
    }
}
