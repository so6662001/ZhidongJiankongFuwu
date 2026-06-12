package com.company.monitor.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规范化的 API 监控规格（内部统一模型）。
 * 由「清单导入」或「OpenAPI 解析」产出，再映射为 HertzBeat 监控请求。
 */
@Data
public class ApiMonitorSpec {

    /** 监控项名称（HertzBeat 与本地登记共用） */
    private String name;

    private String serviceName;

    private String env = "prod";

    /** P0/P1/P2/P3 */
    private String severity = "P2";

    private String owner;

    private String method = "GET";

    /** http / https */
    private String scheme = "https";

    private String host;

    private Integer port;

    /** 相对路径，如 /v1/orders */
    private String uri;

    private Map<String, String> headers = new LinkedHashMap<>();

    private String contentType;

    private String body;

    /** 采集间隔（秒） */
    private Integer intervalSec = 60;

    /** 超时（毫秒） */
    private Integer timeoutMs = 6000;

    /** 期望成功状态码，如 ["200","201"] */
    private List<String> successCodes;

    private boolean enabled = true;

    /** 解析时是否需要人工确认（如含路径参数） */
    private boolean needsReview = false;

    private String reviewNote;

    /** 原始完整 URL（用于本地登记与去重展示） */
    private String fullUrl;

    /** 指定采集器（多探测点/多地域）。为空由系统调度 */
    private String collector;
}
