package com.ayshriv.authplatform.service;

public interface SmsSender {

    void sendOtpSms(String recipientE164, String otpCode);

    String getProviderName();
}
