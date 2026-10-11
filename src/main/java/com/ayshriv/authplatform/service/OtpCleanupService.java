package com.ayshriv.authplatform.service;

import com.ayshriv.authplatform.repository.OtpChallengeRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class OtpCleanupService {

    private static final Logger log = LoggerFactory.getLogger(OtpCleanupService.class);

    private final OtpChallengeRepository otpChallengeRepository;

    public OtpCleanupService(OtpChallengeRepository otpChallengeRepository) {
        this.otpChallengeRepository = otpChallengeRepository;
    }

    @Scheduled(cron = "${app.otp.cleanup-cron:0 */15 * * * *}")
    @SchedulerLock(name = "otpCleanupTask", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    @Transactional
    public void cleanupExpiredChallenges() {
        // Challenges that expired or were consumed more than 1 hour ago
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        log.debug("Running scheduled OTP challenges cleanup with cutoff: {}", cutoff);
        int deletedCount = otpChallengeRepository.deleteExpiredOrConsumedBefore(cutoff);
        if (deletedCount > 0) {
            log.info("Scheduled OTP cleanup deleted {} expired/consumed challenges", deletedCount);
        }
    }
}
