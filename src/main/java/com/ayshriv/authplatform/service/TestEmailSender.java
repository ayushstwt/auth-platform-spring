package com.ayshriv.authplatform.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class TestEmailSender implements EmailSender {

    private String lastRecipient;
    private String lastCode;
    private String lastType;

    @Override
    public void sendVerificationEmail(String recipientEmail, String recipientName, String otpCode) {
        this.lastRecipient = recipientEmail;
        this.lastCode = otpCode;
        this.lastType = "VERIFICATION";
    }

    @Override
    public void sendLoginOtpEmail(String recipientEmail, String recipientName, String otpCode) {
        this.lastRecipient = recipientEmail;
        this.lastCode = otpCode;
        this.lastType = "LOGIN";
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String recipientName, String otpCode) {
        this.lastRecipient = recipientEmail;
        this.lastCode = otpCode;
        this.lastType = "PASSWORD_RESET";
    }

    @Override
    public void sendAccountLinkedEmail(String recipientEmail, String recipientName, String provider) {
        this.lastRecipient = recipientEmail;
        this.lastCode = provider;
        this.lastType = "ACCOUNT_LINKED";
    }

    public String getLastRecipient() {
        return lastRecipient;
    }

    public String getLastCode() {
        return lastCode;
    }

    public String getLastType() {
        return lastType;
    }

    public void clear() {
        this.lastRecipient = null;
        this.lastCode = null;
        this.lastType = null;
    }
}
