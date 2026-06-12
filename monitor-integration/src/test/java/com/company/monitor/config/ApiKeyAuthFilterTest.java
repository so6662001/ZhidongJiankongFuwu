package com.company.monitor.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class ApiKeyAuthFilterTest {

    private HttpServletRequest req(String uri) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getRequestURI()).thenReturn(uri);
        return r;
    }

    private ApiKeyAuthFilter filter(String apiKey, String ssoHeader) {
        IntegrationProperties p = new IntegrationProperties();
        p.setApiKey(apiKey);
        p.setSsoTrustedHeader(ssoHeader);
        return new ApiKeyAuthFilter(p);
    }

    @Test
    void shouldNotFilter_webhookHealthStatic() {
        ApiKeyAuthFilter f = filter("k", null);
        assertTrue(f.shouldNotFilter(req("/api/v1/webhook/hertzbeat")));
        assertTrue(f.shouldNotFilter(req("/actuator/health")));
        assertTrue(f.shouldNotFilter(req("/health")));
        assertTrue(f.shouldNotFilter(req("/ui/index.html")));
        assertFalse(f.shouldNotFilter(req("/api/v1/alerts")));
        assertFalse(f.shouldNotFilter(req("/actuator/prometheus")));
    }

    @Test
    void missingApiKey_returns401() throws Exception {
        ApiKeyAuthFilter f = filter("secret", null);
        HttpServletRequest r = req("/api/v1/alerts");
        when(r.getHeader("X-Api-Key")).thenReturn(null);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);

        f.doFilterInternal(r, resp, chain);

        verify(resp).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void correctApiKey_passes() throws Exception {
        ApiKeyAuthFilter f = filter("secret", null);
        HttpServletRequest r = req("/api/v1/alerts");
        when(r.getHeader("X-Api-Key")).thenReturn("secret");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        f.doFilterInternal(r, resp, chain);

        verify(chain).doFilter(r, resp);
        verify(resp, never()).setStatus(anyInt());
    }

    @Test
    void ssoHeaderPresent_passes() throws Exception {
        ApiKeyAuthFilter f = filter("secret", "X-Auth-User");
        HttpServletRequest r = req("/api/v1/alerts");
        when(r.getHeader("X-Auth-User")).thenReturn("alice");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        f.doFilterInternal(r, resp, chain);

        verify(chain).doFilter(r, resp);
    }

    @Test
    void noAuthConfigured_passesThrough() throws Exception {
        ApiKeyAuthFilter f = filter(null, null);
        HttpServletRequest r = req("/api/v1/alerts");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        f.doFilterInternal(r, resp, chain);

        verify(chain).doFilter(r, resp);
    }
}
