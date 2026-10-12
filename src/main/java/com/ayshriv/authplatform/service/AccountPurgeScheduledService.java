package com.ayshriv.authplatform.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AccountPurgeScheduledService {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeScheduledService.class);

    private final UserService userService;
    private final int retentionDays;

    public AccountPurgeScheduledService(UserService userService,
                                       @Value("${app.retention.account-purge-days:30}") int retentionDays) {
        this.userService = userService;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 0 2 * * *") // Daily at 02:00 UTC
    @SchedulerLock(name = "AccountPurgeTask", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void executeAccountPurge() {
        log.info("Starting scheduled account purge for accounts soft-deleted > {} days ago", retentionDays);
        try {
            int purged = userService.hardPurgeRetentionUsers(retentionDays);
            log.info("Account purge completed successfully: {} accounts permanently removed", purged);
        } catch (Exception ex) {
            log.error("Failed during scheduled account purge: {}", ex.getMessage(), ex);
        }
    }
}
