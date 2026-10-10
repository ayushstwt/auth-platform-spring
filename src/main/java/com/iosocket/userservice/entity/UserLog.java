package com.iosocket.userservice.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "USER_LOG")
@SQLRestriction("IS_DELETED = false")
public class UserLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "USER_ID")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Basic(optional = false)
    @Column(name = "ACTION", nullable = false, length = 50)
    private LogAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "SUB_ACTION", length = 100)
    private LogSubAction subAction;

    public UserLog() {
        super();
    }

    public UserLog(Long id) {
        super(id);
    }

    public UserLog(Long userId, LogAction action, LogSubAction subAction) {
        super();
        this.userId = userId;
        this.action = action;
        this.subAction = subAction;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LogAction getAction() {
        return action;
    }

    public void setAction(LogAction action) {
        this.action = action;
    }

    public LogSubAction getSubAction() {
        return subAction;
    }

    public void setSubAction(LogSubAction subAction) {
        this.subAction = subAction;
    }
}
