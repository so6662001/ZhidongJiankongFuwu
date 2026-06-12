package com.company.monitor.dto;

import lombok.Data;

/**
 * OpenAPI 自动导入请求。openapiContent 与 openapiUrl 二选一。
 */
@Data
public class OpenApiImportRequest {

    /** OpenAPI/Swagger 文档内容（JSON 或 YAML 文本） */
    private String openapiContent;

    /** OpenAPI 文档 URL（由集成层拉取） */
    private String openapiUrl;

    /** 仅预览不写入 */
    private boolean dryRun = true;

    /** 服务名（缺省取 OpenAPI info.title） */
    private String serviceName;

    /** 告警等级，默认 P2 */
    private String severity = "P2";

    /** 采集间隔（秒），默认 60 */
    private Integer intervalSec = 60;

    /** 超时（毫秒），默认 6000 */
    private Integer timeoutMs = 6000;

    /** 仅导入这些方法（默认 GET，避免误触发写操作） */
    private java.util.List<String> includeMethods = java.util.List.of("GET");
}
