package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.util.LogMaskingUtils;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

@Service
@Profile("prod")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final JavaMailSender javaMailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties appProperties;

    public SmtpEmailSender(JavaMailSender javaMailSender,
                           TemplateEngine templateEngine,
                           AppProperties appProperties) {
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
        this.appProperties = appProperties;
    }

    @Override
    @Async("emailTaskExecutor")
    public void sendVerificationEmail(String recipientEmail, String recipientName, String otpCode) {
        Context context = new Context();
        context.setVariable("name", recipientName);
        context.setVariable("code", otpCode);
        context.setVariable("ttlMinutes", appProperties.otp().ttlMinutes());

        String htmlBody = templateEngine.process("mail/verify-email.html", context);
        String textBody = templateEngine.process("mail/verify-email.txt", context);

        sendWithRetry(recipientEmail, "Verify your email address", htmlBody, textBody);
    }

    @Override
    @Async("emailTaskExecutor")
    public void sendLoginOtpEmail(String recipientEmail, String recipientName, String otpCode) {
        Context context = new Context();
        context.setVariable("name", recipientName);
        context.setVariable("code", otpCode);
        context.setVariable("ttlMinutes", appProperties.otp().ttlMinutes());

        String htmlBody = templateEngine.process("mail/login-otp.html", context);
        String textBody = templateEngine.process("mail/login-otp.txt", context);

        sendWithRetry(recipientEmail, "Your login passcode", htmlBody, textBody);
    }

    @Override
    @Async("emailTaskExecutor")
    public void sendPasswordResetEmail(String recipientEmail, String recipientName, String otpCode) {
        Context context = new Context();
        context.setVariable("name", recipientName);
        context.setVariable("code", otpCode);
        context.setVariable("ttlMinutes", appProperties.otp().ttlMinutes());

        String htmlBody = templateEngine.process("mail/password-reset.html", context);
        String textBody = templateEngine.process("mail/password-reset.txt", context);

        sendWithRetry(recipientEmail, "Reset your password", htmlBody, textBody);
    }

    @Override
    @Async("emailTaskExecutor")
    public void sendAccountLinkedEmail(String recipientEmail, String recipientName, String provider) {
        Context context = new Context();
        context.setVariable("name", recipientName);
        context.setVariable("provider", provider);

        String htmlBody = templateEngine.process("mail/account-linked.html", context);
        String textBody = templateEngine.process("mail/account-linked.txt", context);

        sendWithRetry(recipientEmail, "Security alert: " + provider + " account linked", htmlBody, textBody);
    }

    @Override
    @Async("emailTaskExecutor")
    public void sendMfaEnabledEmail(String recipientEmail, String recipientName) {
        Context context = new Context();
        context.setVariable("name", recipientName);

        String htmlBody = templateEngine.process("mail/mfa-enabled.html", context);
        String textBody = templateEngine.process("mail/mfa-enabled.txt", context);

        sendWithRetry(recipientEmail, "Two-Factor Authentication Enabled", htmlBody, textBody);
    }

    @Override
    @Async("emailTaskExecutor")
    public void sendMfaDisabledEmail(String recipientEmail, String recipientName) {
        Context context = new Context();
        context.setVariable("name", recipientName);

        String htmlBody = templateEngine.process("mail/mfa-disabled.html", context);
        String textBody = templateEngine.process("mail/mfa-disabled.txt", context);

        sendWithRetry(recipientEmail, "Two-Factor Authentication Disabled", htmlBody, textBody);
    }

    @Override
    @Async("emailTaskExecutor")
    public void sendRecoveryCodesRegeneratedEmail(String recipientEmail, String recipientName) {
        Context context = new Context();
        context.setVariable("name", recipientName);

        String htmlBody = templateEngine.process("mail/recovery-codes-regenerated.html", context);
        String textBody = templateEngine.process("mail/recovery-codes-regenerated.txt", context);

        sendWithRetry(recipientEmail, "MFA Recovery Codes Regenerated", htmlBody, textBody);
    }

    private void sendWithRetry(String recipientEmail, String subject, String htmlBody, String textBody) {

        int attempt = 0;
        long backoff = INITIAL_BACKOFF_MS;

        while (attempt < MAX_ATTEMPTS) {
            attempt++;
            try {
                MimeMessage message = javaMailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

                helper.setFrom(appProperties.email().fromAddress(), appProperties.email().fromName());
                helper.setTo(recipientEmail);
                helper.setSubject(subject);
                helper.setText(textBody, htmlBody);

                javaMailSender.send(message);
                log.info("Email successfully sent to {} with subject: {}", LogMaskingUtils.maskEmail(recipientEmail), subject);
                return;
            } catch (Exception e) {
                log.warn("Failed to send email to {} on attempt {}/{}: {}",
                        LogMaskingUtils.maskEmail(recipientEmail), attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(backoff);
                        backoff *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Interrupted during email retry backoff", ie);
                        return;
                    }
                } else {
                    log.error("Exhausted retries sending email to {}", LogMaskingUtils.maskEmail(recipientEmail), e);
                }
            }
        }
    }
}
