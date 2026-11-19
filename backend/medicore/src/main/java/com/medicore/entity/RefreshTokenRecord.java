package com.medicore.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token")
public class RefreshTokenRecord {
    @Id
    @Column(name = "token_id", length = 36, columnDefinition = "char(36)")
    public String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    public UserAccount user;

    @Column(name = "family_id", nullable = false, length = 36, columnDefinition = "char(36)")
    public String familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    public String tokenHash;

    @Column(name = "issued_at", nullable = false)
    public LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    public LocalDateTime expiresAt;

    @Column(name = "used_at")
    public LocalDateTime usedAt;

    @Column(name = "revoked_at")
    public LocalDateTime revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_token_id")
    public RefreshTokenRecord replacedBy;
}
