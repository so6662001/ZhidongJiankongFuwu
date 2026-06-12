package com.company.monitor.config;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端 SPA（Vue history 模式）深链转发：/ui/** 的非静态资源请求转发到 /ui/index.html，
 * 由前端路由接管，避免刷新深链 404。
 */
@Hidden
@Controller
public class SpaForwardController {

    @GetMapping({"/ui", "/ui/", "/ui/{path:[^\\.]*}"})
    public String forward() {
        return "forward:/ui/index.html";
    }
}
