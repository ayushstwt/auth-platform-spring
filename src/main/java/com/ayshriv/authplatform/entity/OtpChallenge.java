package com.ayshriv.authplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Entity
@Table(name = "otp_challenges")
@SQLRestriction("IS_DELETED = false")
@Getter
@Setter
@NoArgsConstructor
public class OtpChallenge extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private OtpChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private OtpPurpose purpose;

    @Column(name = "destination", nullable = false, length = 255)
    private String destination;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 5;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "request_ip", length = 45)
    private String requestIp;

    public OtpChallenge(User user, OtpChannel channel, OtpPurpose purpose, String destination,
                        String codeHash, Instant expiresAt, int maxAttempts, String requestIp) {
        this.user = user;
        this.channel = channel;
        this.purpose = purpose;
        this.destination = destination;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.maxAttempts = maxAttempts;
        this.requestIp = requestIp;
        this.attempts = 0;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isAttemptsExhausted() {
        return attempts >= maxAttempts;
    }

    public boolean isValid() {
        return isActive() && !isDeleted() && !isExpired() && !isConsumed() && !isAttemptsExhausted();
    }
}
