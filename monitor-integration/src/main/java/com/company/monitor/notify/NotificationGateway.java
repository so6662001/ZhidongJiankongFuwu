package com.company.monitor.notify;

import com.company.monitor.config.WeComProperties;
import com.company.monitor.entity.Alert;
import com.company.monitor.entity.NotifyLog;
import com.company.monitor.entity.ServiceOwner;
import com.company.monitor.mapper.NotifyLogMapper;
import com.company.monitor.mapper.ServiceOwnerMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知网关：按服务负责人路由，发送企业微信应用消息(精准@人) + 邮件，并记录回执。
 */
@Slf4j
@Component
public class NotificationGateway {

    private final WeComAppClient weComAppClient;
    private final EmailNotifier emailNotifier;
    private final TemplateRenderer renderer;
    private final ServiceOwnerMapper serviceOwnerMapper;
    private final NotifyLogMapper notifyLogMapper;
    private final WeComProperties weComProperties;

    public NotificationGateway(WeComAppClient weComAppClient, EmailNotifier emailNotifier,
                               TemplateRenderer renderer, ServiceOwnerMapper serviceOwnerMapper,
                               NotifyLogMapper notifyLogMapper, WeComProperties weComProperties) {
        this.weComAppClient = weComAppClient;
        this.emailNotifier = emailNotifier;
        this.renderer = renderer;
        this.serviceOwnerMapper = serviceOwnerMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.weComProperties = weComProperties;
    }

    public void notify(Alert alert, boolean recovered) {
        ServiceOwner owner = findOwner(alert.getServiceName());
        List<String> wecomUserIds = resolveWecomUserIds(owner);
        List<String> emails = resolveEmails(owner);

        String title = renderer.title(alert, recovered);
        // 企业微信应用消息（精准@人）
        if (weComProperties.isConfigured() && !wecomUserIds.isEmpty()) {
            sendWecom(alert, recovered, wecomUserIds, title);
        } else {
            log.info("跳过企业微信通知(未配置或无接收人), alertId={}", alert.getId());
        }
        // 邮件
        if (!emails.isEmpty()) {
            sendEmail(alert, recovered, emails, title);
        } else {
            log.info("跳过邮件通知(无接收人), alertId={}", alert.getId());
        }
    }

    private void sendWecom(Alert alert, boolean recovered, List<String> userIds, String title) {
        long start = System.currentTimeMillis();
        String desc = renderer.wecomDescription(alert, recovered);
        NotifyLog logRec = baseLog(alert, "wecom_app", String.join(",", userIds), desc);
        try {
            weComAppClient.sendTextCard(userIds, title, desc, renderer.detailUrl(alert));
            logRec.setSuccess(1);
        } catch (Exception e) {
            logRec.setSuccess(0);
            logRec.setErrorMsg(truncate(e.getMessage(), 500));
            log.warn("企业微信通知失败 alertId={}: {}", alert.getId(), e.getMessage());
        } finally {
            logRec.setCostMs((int) (System.currentTimeMillis() - start));
            notifyLogMapper.insert(logRec);
        }
    }

    private void sendEmail(Alert alert, boolean recovered, List<String> emails, String title) {
        long start = System.currentTimeMillis();
        String html = renderer.emailHtml(alert, recovered);
        NotifyLog logRec = baseLog(alert, "email", String.join(",", emails), html);
        int retry = 0;
        Exception last = null;
        for (; retry < 3; retry++) {
            try {
                emailNotifier.send(emails, title, html);
                logRec.setSuccess(1);
                last = null;
                break;
            } catch (Exception e) {
                last = e;
                sleep((long) Math.pow(2, retry) * 500);
            }
        }
        if (last != null) {
            logRec.setSuccess(0);
            logRec.setErrorMsg(truncate(last.getMessage(), 500));
            log.warn("邮件通知失败 alertId={}: {}", alert.getId(), last.getMessage());
        }
        logRec.setRetryCount(retry);
        logRec.setCostMs((int) (System.currentTimeMillis() - start));
        notifyLogMapper.insert(logRec);
    }

    private NotifyLog baseLog(Alert alert, String channel, String receiver, String content) {
        NotifyLog l = new NotifyLog();
        l.setAlertId(alert.getId());
        l.setChannelType(channel);
        l.setReceiver(receiver);
        l.setContent(truncate(content, 4000));
        l.setSuccess(0);
        l.setRetryCount(0);
        l.setSentAt(LocalDateTime.now());
        return l;
    }

    private ServiceOwner findOwner(String serviceName) {
        if (serviceName == null) {
            return null;
        }
        return serviceOwnerMapper.selectOne(new QueryWrapper<ServiceOwner>()
                .eq("service_name", serviceName).eq("enabled", 1).last("limit 1"));
    }

    private List<String> resolveWecomUserIds(ServiceOwner owner) {
        if (owner != null && owner.getWecomUserids() != null && !owner.getWecomUserids().isBlank()) {
            return split(owner.getWecomUserids());
        }
        return split(weComProperties.getDefaultUserids());
    }

    private List<String> resolveEmails(ServiceOwner owner) {
        if (owner != null && owner.getEmailList() != null && !owner.getEmailList().isBlank()) {
            return split(owner.getEmailList());
        }
        return List.of();
    }

    private List<String> split(String s) {
        if (s == null || s.isBlank()) {
            return List.of();
        }
        return Arrays.stream(s.split("[,;]"))
                .map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toList());
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
