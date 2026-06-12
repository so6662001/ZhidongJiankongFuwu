package com.company.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * HertzBeat 引擎连接配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "hertzbeat")
public class HertzBeatProperties {

    /** HertzBeat 基础地址，如 http://hertzbeat:1157 */
    private String baseUrl = "http://localhost:1157";

    /** 管理员账号 */
    private String username = "admin";

    /** 管理员密码 */
    private String password = "hertzbeat";

    /** 请求超时（秒） */
    private int timeoutSeconds = 10;
}
