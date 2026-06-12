package com.company.monitor.service;

import com.company.monitor.common.BizException;
import com.company.monitor.config.IntegrationProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SsrfGuardTest {

    private SsrfGuard guard(boolean allowPrivate, List<String> allowHosts) {
        IntegrationProperties p = new IntegrationProperties();
        p.setOpenapiAllowPrivateNetwork(allowPrivate);
        if (allowHosts != null) p.setOpenapiAllowedHosts(allowHosts);
        return new SsrfGuard(p);
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThrows(BizException.class, () -> guard(false, null).validateFetchUrl("ftp://example.com/x"));
    }

    @Test
    void rejectsLoopback() {
        assertThrows(BizException.class, () -> guard(false, null).validateFetchUrl("http://127.0.0.1/x"));
    }

    @Test
    void rejectsLinkLocalMetadata() {
        assertThrows(BizException.class, () -> guard(false, null).validateFetchUrl("http://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void rejectsPrivateByDefault() {
        assertThrows(BizException.class, () -> guard(false, null).validateFetchUrl("http://10.0.0.5/openapi"));
    }

    @Test
    void allowsPrivateWhenEnabled() {
        assertDoesNotThrow(() -> guard(true, null).validateFetchUrl("http://10.0.0.5/openapi"));
    }

    @Test
    void enforcesAllowlistBeforeResolution() {
        SsrfGuard g = guard(true, List.of("good.com"));
        assertThrows(BizException.class, () -> g.validateFetchUrl("http://bad.com/x"));
    }
}
