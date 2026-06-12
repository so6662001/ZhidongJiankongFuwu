package com.company.monitor.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入结果。
 */
@Data
public class ImportResult {

    private boolean dryRun;
    private int total;
    private int created;
    private int updated;
    private int failed;
    private int skipped;
    private List<Item> details = new ArrayList<>();

    @Data
    public static class Item {
        private String name;
        private String url;
        private String serviceName;
        /** created / updated / failed / skipped / preview */
        private String action;
        private Long hzbMonitorId;
        private boolean needsReview;
        private String message;

        public static Item of(String action, ApiMonitorSpec spec) {
            Item it = new Item();
            it.action = action;
            it.name = spec.getName();
            it.url = spec.getFullUrl();
            it.serviceName = spec.getServiceName();
            it.needsReview = spec.isNeedsReview();
            it.message = spec.getReviewNote();
            return it;
        }
    }

    public void add(Item item) {
        details.add(item);
        total++;
        switch (item.getAction()) {
            case "created" -> created++;
            case "updated" -> updated++;
            case "failed" -> failed++;
            case "skipped" -> skipped++;
            default -> { }
        }
    }
}
