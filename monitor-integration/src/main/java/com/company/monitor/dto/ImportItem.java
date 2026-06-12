package com.company.monitor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 公司接口清单导入项（JSON 数组元素）。
 */
@Data
public class ImportItem {

    private String name;

    @NotBlank(message = "url 不能为空")
    private String url;

    private String method = "GET";

    private Map<String, String> headers;

    private String contentType;

    private String body;

    private String serviceName;

    private String env;

    private String severity;

    private String owner;

    private Integer intervalSec;

    private Integer timeoutMs;

    private List<String> successCodes;
}
