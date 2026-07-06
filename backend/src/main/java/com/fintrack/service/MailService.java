package com.fintrack.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Thin mail wrapper. When SMTP is configured (spring.mail.host / SMTP_HOST),
 * mails go out for real; otherwise the full message is logged so the flows
 * stay testable in local dev without any external service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    // Auto-configured only when spring.mail.host is set, hence the provider.
    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${app.mail-from:no-reply@tally.local}")
    private String from;

    // The property defaults to empty, which still creates a (useless) sender
    // bean — treat blank host as "SMTP not configured".
    @Value("${spring.mail.host:}")
    private String smtpHost;

    @Async
    public void send(String to, String subject, String body) {
        JavaMailSender sender = smtpHost == null || smtpHost.isBlank() ? null : mailSender.getIfAvailable();
        if (sender == null) {
            log.info("[DEV MAIL — SMTP not configured] to={} subject=\"{}\"\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception e) {
            // Mail failures must never break auth flows; the user can retry.
            log.error("Failed to send mail to {}: {}", to, e.getMessage());
        }
    }
}
