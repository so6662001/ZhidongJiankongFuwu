package com.company.monitor.notify;

import com.company.monitor.config.IntegrationProperties;
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
    private final IntegrationProperties integrationProperties;

    public NotificationGateway(WeComAppClient weComAppClient, EmailNotifier emailNotifier,
                               TemplateRenderer renderer, ServiceOwnerMapper serviceOwnerMapper,
                               NotifyLogMapper notifyLogMapper, WeComProperties weComProperties,
                               IntegrationProperties integrationProperties) {
        this.weComAppClient = weComAppClient;
        this.emailNotifier = emailNotifier;
        this.renderer = renderer;
        this.serviceOwnerMapper = serviceOwnerMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.weComProperties = weComProperties;
        this.integrationProperties = integrationProperties;
    }

    public void notify(Alert alert, boolean recovered) {
        ServiceOwner owner = findOwner(alert.getServiceName());
        dispatch(alert, recovered, resolveWecomUserIds(owner), resolveEmails(owner), renderer.title(alert, recovered));
    }

    /**
     * 升级通知：发给升级接收人（额外的人/渠道），标题带升级标识。
     */
    public void notifyEscalation(Alert alert, List<String> wecomUserIds, List<String> emails) {
        String title = "【告警升级】" + renderer.title(alert, false);
        dispatch(alert, false, wecomUserIds, emails, title);
    }

    /**
     * 按通知模式（failover/all）派发到企业微信/邮件。
     */
    private void dispatch(Alert alert, boolean recovered, List<String> wecomUserIds, List<String> emails, String title) {
        boolean failover = !"all".equalsIgnoreCase(integrationProperties.getNotifyMode());
        boolean wecomAvailable = weComProperties.isConfigured() && !wecomUserIds.isEmpty();
        boolean emailAvailable = !emails.isEmpty();

        boolean wecomOk = false;
        if (wecomAvailable) {
            wecomOk = sendWecom(alert, recovered, wecomUserIds, title);
        } else {
            log.info("跳过企业微信通知(未配置或无接收人), alertId={}", alert.getId());
        }

        // failover：企业微信成功则不再发邮件；失败或不可用则兜底邮件
        boolean sendEmailNow = emailAvailable && (!failover || !wecomOk);
        if (sendEmailNow) {
            boolean emailOk = sendEmail(alert, recovered, emails, title);
            if (failover && !wecomOk && emailOk) {
                log.info("企业微信不可用/失败，已兜底邮件通知 alertId={}", alert.getId());
            }
        } else if (!emailAvailable && (!wecomAvailable || !wecomOk)) {
            log.warn("告警无任何可用通知渠道送达 alertId={}", alert.getId());
        }
    }

    private boolean sendWecom(Alert alert, boolean recovered, List<String> userIds, String title) {
        long start = System.currentTimeMillis();
        String desc = renderer.wecomDescription(alert, recovered);
        NotifyLog logRec = baseLog(alert, "wecom_app", String.join(",", userIds), desc);
        boolean ok = false;
        try {
            weComAppClient.sendTextCard(userIds, title, desc, renderer.detailUrl(alert));
            logRec.setSuccess(1);
            ok = true;
        } catch (Exception e) {
            logRec.setSuccess(0);
            logRec.setErrorMsg(truncate(e.getMessage(), 500));
            log.warn("企业微信通知失败 alertId={}: {}", alert.getId(), e.getMessage());
        } finally {
            logRec.setCostMs((int) (System.currentTimeMillis() - start));
            notifyLogMapper.insert(logRec);
        }
        return ok;
    }

    private boolean sendEmail(Alert alert, boolean recovered, List<String> emails, String title) {
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
        boolean ok = last == null;
        if (last != null) {
            logRec.setSuccess(0);
            logRec.setErrorMsg(truncate(last.getMessage(), 500));
            log.warn("邮件通知失败 alertId={}: {}", alert.getId(), last.getMessage());
        }
        logRec.setRetryCount(retry);
        logRec.setCostMs((int) (System.currentTimeMillis() - start));
        notifyLogMapper.insert(logRec);
        return ok;
    }

    /** 供测试/升级使用：解析服务负责人接收人。 */
    public List<String> wecomUserIdsForService(String serviceName) {
        return resolveWecomUserIds(findOwner(serviceName));
    }

    public List<String> emailsForService(String serviceName) {
        return resolveEmails(findOwner(serviceName));
    }

    public void notifyToReceivers(Alert alert, boolean recovered, List<String> wecomUserIds, List<String> emails) {
        dispatch(alert, recovered, wecomUserIds, emails, renderer.title(alert, recovered));
    }

    public List<String> splitList(String s) {
        return split(s);
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
