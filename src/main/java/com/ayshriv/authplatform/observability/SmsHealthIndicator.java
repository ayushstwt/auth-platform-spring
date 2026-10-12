package com.ayshriv.authplatform.observability;

import com.ayshriv.authplatform.service.SmsSender;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("smsServiceHealth")
public class SmsHealthIndicator implements HealthIndicator {

    private final SmsSender smsSender;

    public SmsHealthIndicator(SmsSender smsSender) {
        this.smsSender = smsSender;
    }

    @Override
    public Health health() {
        if (smsSender != null) {
            return Health.up()
                    .withDetail("provider", smsSender.getClass().getSimpleName())
                    .build();
        }
        return Health.down().withDetail("reason", "No SMS sender configured").build();
    }
}
