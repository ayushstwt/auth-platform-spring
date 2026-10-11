package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.util.LogMaskingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
public class DevLoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(DevLoggingEmailSender.class);

    @Override
    public void sendVerificationEmail(String recipientEmail, String recipientName, String otpCode) {
        log.info("[DEV EMAIL] To: {} ({}) | Subject: Verify Your Email | OTP: {}",
                LogMaskingUtils.maskEmail(recipientEmail), recipientName, otpCode);
        System.out.println("==================================================");
        System.out.println(" [DEV EMAIL SENDER] VERIFY EMAIL OTP");
        System.out.println(" Recipient: " + recipientEmail);
        System.out.println(" Code: " + otpCode);
        System.out.println("==================================================");
    }

    @Override
    public void sendLoginOtpEmail(String recipientEmail, String recipientName, String otpCode) {
        log.info("[DEV EMAIL] To: {} ({}) | Subject: Your Login Passcode | OTP: {}",
                LogMaskingUtils.maskEmail(recipientEmail), recipientName, otpCode);
        System.out.println("==================================================");
        System.out.println(" [DEV EMAIL SENDER] LOGIN OTP");
        System.out.println(" Recipient: " + recipientEmail);
        System.out.println(" Code: " + otpCode);
        System.out.println("==================================================");
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String recipientName, String otpCode) {
        log.info("[DEV EMAIL] To: {} ({}) | Subject: Reset Your Password | OTP: {}",
                LogMaskingUtils.maskEmail(recipientEmail), recipientName, otpCode);
        System.out.println("==================================================");
        System.out.println(" [DEV EMAIL SENDER] PASSWORD RESET OTP");
        System.out.println(" Recipient: " + recipientEmail);
        System.out.println(" Code: " + otpCode);
        System.out.println("==================================================");
    }

    @Override
    public void sendAccountLinkedEmail(String recipientEmail, String recipientName, String provider) {
        log.info("[DEV EMAIL] To: {} ({}) | Subject: Account Linked | Provider: {}",
                LogMaskingUtils.maskEmail(recipientEmail), recipientName, provider);
        System.out.println("==================================================");
        System.out.println(" [DEV EMAIL SENDER] ACCOUNT LINKED NOTIFICATION");
        System.out.println(" Recipient: " + recipientEmail);
        System.out.println(" Provider: " + provider);
        System.out.println("==================================================");
    }

    @Override
    public void sendMfaEnabledEmail(String recipientEmail, String recipientName) {
        log.info("[DEV EMAIL] To: {} ({}) | Subject: Two-Factor Authentication Enabled",
                LogMaskingUtils.maskEmail(recipientEmail), recipientName);
        System.out.println("==================================================");
        System.out.println(" [DEV EMAIL SENDER] MFA ENABLED NOTIFICATION");
        System.out.println(" Recipient: " + recipientEmail);
        System.out.println("==================================================");
    }

    @Override
    public void sendMfaDisabledEmail(String recipientEmail, String recipientName) {
        log.info("[DEV EMAIL] To: {} ({}) | Subject: Two-Factor Authentication Disabled",
                LogMaskingUtils.maskEmail(recipientEmail), recipientName);
        System.out.println("==================================================");
        System.out.println(" [DEV EMAIL SENDER] MFA DISABLED NOTIFICATION");
        System.out.println(" Recipient: " + recipientEmail);
        System.out.println("==================================================");
    }

    @Override
    public void sendRecoveryCodesRegeneratedEmail(String recipientEmail, String recipientName) {
        log.info("[DEV EMAIL] To: {} ({}) | Subject: MFA Recovery Codes Regenerated",
                LogMaskingUtils.maskEmail(recipientEmail), recipientName);
        System.out.println("==================================================");
        System.out.println(" [DEV EMAIL SENDER] RECOVERY CODES REGENERATED NOTIFICATION");
        System.out.println(" Recipient: " + recipientEmail);
        System.out.println("==================================================");
    }
}

