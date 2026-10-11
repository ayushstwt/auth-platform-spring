package com.ayshriv.authplatform.repository;

import com.ayshriv.authplatform.entity.OtpPurpose;
import com.ayshriv.authplatform.entity.OtpChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    Optional<OtpChallenge> findTopByDestinationAndPurposeAndActiveTrueAndDeletedFalseOrderByCreatedOnDesc(
            String destination,
            OtpPurpose purpose
    );

    Optional<OtpChallenge> findTopByUserIdAndPurposeAndActiveTrueAndDeletedFalseOrderByCreatedOnDesc(
            Long userId,
            OtpPurpose purpose
    );

    long countByDestinationAndCreatedOnAfter(String destination, Instant since);

    @Modifying
    @Query("UPDATE OtpChallenge o SET o.active = false WHERE o.destination = :destination AND o.purpose = :purpose AND o.active = true")
    void deactivateExistingChallengesByDestination(@Param("destination") String destination, @Param("purpose") OtpPurpose purpose);

    @Modifying
    @Query("UPDATE OtpChallenge o SET o.active = false WHERE o.user.id = :userId AND o.purpose = :purpose AND o.active = true")
    void deactivateExistingChallengesByUserId(@Param("userId") Long userId, @Param("purpose") OtpPurpose purpose);

    @Modifying
    @Query("DELETE FROM OtpChallenge o WHERE o.expiresAt < :cutoff OR (o.consumedAt IS NOT NULL AND o.consumedAt < :cutoff)")
    int deleteExpiredOrConsumedBefore(@Param("cutoff") Instant cutoff);
}
