package com.ayshriv.authplatform.service;

public interface EmailSender {

    void sendVerificationEmail(String recipientEmail, String recipientName, String otpCode);

    void sendLoginOtpEmail(String recipientEmail, String recipientName, String otpCode);

    void sendPasswordResetEmail(String recipientEmail, String recipientName, String otpCode);
}
