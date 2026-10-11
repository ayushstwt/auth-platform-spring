package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.util.LogMaskingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void sendOtpSms(String recipientE164, String otpCode) {
        log.info("[DEV SMS] To: {} | Your verification code is: {}", LogMaskingUtils.maskPhone(recipientE164), otpCode);
        System.out.println("==================================================");
        System.out.println(" [DEV SMS SENDER] VERIFICATION CODE");
        System.out.println(" Recipient: " + recipientE164);
        System.out.println(" Code: " + otpCode);
        System.out.println("==================================================");
    }

    @Override
    public String getProviderName() {
        return "logging";
    }
}
