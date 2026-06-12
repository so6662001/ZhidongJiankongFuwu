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
        // 仅拦截 /api/v1 管理接口；Webhook 走自己的 token 校验
        if (uri.startsWith("/api/v1/webhook/")) {
            return true;
        }
        return !uri.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String configured = properties.getApiKey();
        if (configured == null || configured.isBlank()) {
            // 未配置：放行但告警（建议生产配置）
            chain.doFilter(request, response);
            return;
        }
        String provided = request.getHeader(HEADER);
        if (provided == null || !constantTimeEquals(configured, provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    Result.error(401, "missing or invalid X-Api-Key")));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] y = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(x, y);
    }
}
