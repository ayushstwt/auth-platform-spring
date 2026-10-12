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
        name = "FEDERATED_IDENTITIES",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_federated_identity_provider_subject", columnNames = {"PROVIDER", "PROVIDER_SUBJECT"})
        }
)
@SQLRestriction("IS_DELETED = false")
public class FederatedIdentity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @Basic(optional = false)
    @Column(name = "PROVIDER", nullable = false, length = 50)
    private String provider;

    @Basic(optional = false)
    @Column(name = "PROVIDER_SUBJECT", nullable = false, length = 255)
    private String providerSubject;

    @Basic(optional = false)
    @Column(name = "EMAIL_AT_LINK", nullable = false, length = 255)
    private String emailAtLink;

    @Basic(optional = false)
    @Column(name = "LINKED_AT", nullable = false)
    private Instant linkedAt = Instant.now();

    public FederatedIdentity() {
        super();
    }

    public FederatedIdentity(User user, String provider, String providerSubject, String emailAtLink) {
        super();
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.emailAtLink = emailAtLink;
        this.linkedAt = Instant.now();
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public void setProviderSubject(String providerSubject) {
        this.providerSubject = providerSubject;
    }

    public String getEmailAtLink() {
        return emailAtLink;
    }

    public void setEmailAtLink(String emailAtLink) {
        this.emailAtLink = emailAtLink;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(Instant linkedAt) {
        this.linkedAt = linkedAt;
    }
}
