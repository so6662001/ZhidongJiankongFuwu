package com.company.monitor.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 清单批量导入请求。
 */
@Data
public class ImportRequest {

    @NotEmpty(message = "items 不能为空")
    private List<ImportItem> items;

    /** 仅预览不写入 */
    private boolean dryRun = false;

    /** 默认服务名（item 未指定时使用） */
    private String defaultServiceName;

    /** 默认告警等级 */
    private String defaultSeverity = "P2";

    /** 默认采集间隔（秒） */
    private Integer defaultIntervalSec = 60;

    /** 默认采集器（多探测点），item 未指定时使用 */
    private String defaultCollector;
}
