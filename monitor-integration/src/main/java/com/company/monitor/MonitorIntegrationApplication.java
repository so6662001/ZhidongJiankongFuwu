package com.company.monitor;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.company.monitor.mapper")
public class MonitorIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitorIntegrationApplication.class, args);
    }
}
