package com.company.monitor.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI monitorIntegrationOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("API 监控集成层 - 接口文档")
                .description("HertzBeat 集成增强层：批量纳管、告警归档、企业微信/邮件通知")
                .version("0.1.0"));
    }
}
