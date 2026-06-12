package com.company.monitor.notify;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 邮件发送（HTML）。
 */
@Slf4j
@Component
public class EmailNotifier {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String from;

    public EmailNotifier(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(List<String> to, String subject, String htmlContent) throws Exception {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("邮件发件账号(spring.mail.username)未配置");
        }
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException("无邮件接收人");
        }
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(from);
        helper.setTo(to.toArray(new String[0]));
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
    }
}
