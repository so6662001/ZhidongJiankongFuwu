package com.company.monitor.service;

import com.company.monitor.common.BizException;
import com.company.monitor.config.IntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * SSRF 防护：校验集成层主动拉取的 URL（如 OpenAPI 文档地址）。
 * 始终拦截环回/链路本地(含云元数据 169.254.169.254)/通配地址；
 * 默认拦截私网地址（可配置放开）；可选 host 白名单。
 */
@Slf4j
@Component
public class SsrfGuard {

    private final IntegrationProperties properties;

    public SsrfGuard(IntegrationProperties properties) {
        this.properties = properties;
    }

    public void validateFetchUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (Exception e) {
            throw new BizException("非法 URL: " + url);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BizException("仅允许 http/https 协议: " + url);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BizException("URL 缺少 host: " + url);
        }

        List<String> allow = properties.getOpenapiAllowedHosts();
        if (allow != null && !allow.isEmpty()) {
            boolean ok = allow.stream().map(s -> s.trim().toLowerCase(Locale.ROOT))
                    .anyMatch(h -> !h.isEmpty() && host.toLowerCase(Locale.ROOT).equals(h));
            if (!ok) {
                throw new BizException("目标 host 不在 OpenAPI 白名单内: " + host);
            }
        }

        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new BizException("无法解析 host: " + host);
        }
        for (InetAddress addr : addrs) {
            if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isMulticastAddress()) {
                throw new BizException("禁止访问环回/链路本地/通配地址: " + host + " -> " + addr.getHostAddress());
            }
            if (addr.isSiteLocalAddress() && !properties.isOpenapiAllowPrivateNetwork()) {
                throw new BizException("禁止访问私网地址(可配置 openapi-allow-private-network 放开): "
                        + host + " -> " + addr.getHostAddress());
            }
        }
    }
}
