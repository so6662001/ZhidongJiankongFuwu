package com.company.monitor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.monitor.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 管理 API 鉴权过滤器（X-Api-Key）。
 *
 * <p>保护 /api/v1/** 管理接口；放行健康检查、Swagger 与告警 Webhook（Webhook 用独立 token 校验）。
 * 未配置 api-key 时不拦截（仅适用于内网/测试），启动会打印告警。
 */
@Slf4j
@Order(1)
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Api-Key";

    private final IntegrationProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiKeyAuthFilter(IntegrationProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Webhook 走自己的 token 校验，放行本过滤器
        if (uri.startsWith("/api/v1/webhook/")) {
            return true;
        }
        // 健康检查放行（供探针/HertzBeat 监控）；其余 actuator 端点（prometheus/metrics）需鉴权
        if (uri.equals("/actuator/health") || uri.startsWith("/actuator/health/") || uri.equals("/health")) {
            return true;
        }
        // 拦截管理 API 与受保护的 actuator 指标端点
        return !(uri.startsWith("/api/v1/") || uri.startsWith("/actuator/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String configured = properties.getApiKey();
        String ssoHeader = properties.getSsoTrustedHeader();

        // SSO 网关可信身份头：携带非空即视为已认证（网关需强制 SSO 并剥离伪造头）
        if (ssoHeader != null && !ssoHeader.isBlank()) {
            String user = request.getHeader(ssoHeader);
            if (user != null && !user.isBlank()) {
                chain.doFilter(request, response);
                return;
            }
        }

        if (configured == null || configured.isBlank()) {
            if (ssoHeader == null || ssoHeader.isBlank()) {
                // 未配置任何鉴权：放行但启动已告警
                chain.doFilter(request, response);
                return;
            }
            // 配了 SSO 头但请求未携带 → 拒绝
            unauthorized(response, "missing SSO identity header");
            return;
        }
        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(configured, provided)) {
            unauthorized(response, "missing or invalid X-Api-Key");
            return;
        }
        chain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(401, msg)));
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] y = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(x, y);
    }
}
