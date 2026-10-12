package com.ayshriv.authplatform.service;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
@Primary
public class TestSmsSender implements SmsSender {

    private String lastRecipient;
    private String lastCode;
    private boolean simulateFailure = false;

    @Override
    public void sendOtpSms(String recipientE164, String otpCode) {
        if (simulateFailure) {
            throw new RuntimeException("Simulated provider outage");
        }
        this.lastRecipient = recipientE164;
        this.lastCode = otpCode;
    }

    @Override
    public String getProviderName() {
        return "test";
    }

    public String getLastRecipient() {
        return lastRecipient;
    }

    public String getLastCode() {
        return lastCode;
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public void clear() {
        this.lastRecipient = null;
        this.lastCode = null;
        this.simulateFailure = false;
    }
}
