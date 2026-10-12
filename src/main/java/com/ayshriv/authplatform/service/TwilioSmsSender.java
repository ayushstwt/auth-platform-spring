package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.config.AppProperties;
import com.ayshriv.authplatform.util.LogMaskingUtils;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "twilio")
public class TwilioSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsSender.class);

    private final AppProperties appProperties;

    public TwilioSmsSender(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        String accountSid = appProperties.sms().twilio().accountSid();
        String authToken = appProperties.sms().twilio().authToken();
        if (accountSid != null && !accountSid.isBlank() && authToken != null && !authToken.isBlank()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio SMS Client initialized successfully");
        } else {
            log.warn("Twilio credentials not fully configured; SMS sending may fail in production");
        }
    }

    @Override
    @Async("emailTaskExecutor")
    @CircuitBreaker(name = "smsSender", fallbackMethod = "sendOtpFallback")
    @Retry(name = "smsSender")
    public void sendOtpSms(String recipientE164, String otpCode) {
        String from = appProperties.sms().twilio().fromPhoneNumber();
        String messageBody = "Your verification code is " + otpCode + ". It will expire in "
                + appProperties.otp().ttlMinutes() + " minutes.";

        Message message = Message.creator(
                new PhoneNumber(recipientE164),
                new PhoneNumber(from),
                messageBody
        ).create();

        log.info("Twilio SMS sent to {}. SID: {}", LogMaskingUtils.maskPhone(recipientE164), message.getSid());
    }

    public void sendOtpFallback(String recipientE164, String otpCode, Throwable t) {
        log.error("Twilio SMS circuit-breaker or retry fallback triggered for {}: {}",
                LogMaskingUtils.maskPhone(recipientE164), t.getMessage());
    }

    @Override
    public String getProviderName() {
        return "twilio";
    }
}
