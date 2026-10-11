package com.ayshriv.authplatform.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Entity
@Table(
        name = "USER_RECOVERY_CODES",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_recovery_code_hash", columnNames = {"USER_ID", "CODE_HASH"})
        }
)
@SQLRestriction("IS_DELETED = false")
public class UserRecoveryCode extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Basic(optional = false)
    @Column(name = "CODE_HASH", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "CONSUMED_AT")
    private Instant consumedAt;

    public UserRecoveryCode() {
        super();
    }

    public UserRecoveryCode(User user, String codeHash) {
        super();
        this.user = user;
        this.codeHash = codeHash;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }
}
