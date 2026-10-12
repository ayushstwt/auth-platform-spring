package com.ayshriv.authplatform.repository;

import com.ayshriv.authplatform.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyId(String familyId);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.familyId = :familyId AND r.revokedAt IS NULL")
    int revokeByFamilyId(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.user.id = :userId AND r.familyId <> :excludeFamilyId AND r.revokedAt IS NULL")
    int revokeAllByUserIdExceptFamily(@Param("userId") Long userId, @Param("excludeFamilyId") String excludeFamilyId, @Param("revokedAt") Instant revokedAt);

    @Query("SELECT r FROM RefreshToken r WHERE r.user.id = :userId AND r.revokedAt IS NULL AND r.expiresAt > :now ORDER BY r.createdOn DESC")
    List<RefreshToken> findActiveSessionsByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
