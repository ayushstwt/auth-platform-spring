package com.ayshriv.authplatform.observability;

import com.ayshriv.authplatform.service.EmailSender;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("mailServiceHealth")
public class MailHealthIndicator implements HealthIndicator {

    private final EmailSender emailSender;

    public MailHealthIndicator(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public Health health() {
        if (emailSender != null) {
            return Health.up()
                    .withDetail("provider", emailSender.getClass().getSimpleName())
                    .build();
        }
        return Health.down().withDetail("reason", "No email sender configured").build();
    }
}
