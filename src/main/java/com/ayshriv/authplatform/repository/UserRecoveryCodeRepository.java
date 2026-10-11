package com.ayshriv.authplatform.repository;

import com.ayshriv.authplatform.entity.UserRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRecoveryCodeRepository extends JpaRepository<UserRecoveryCode, Long> {

    List<UserRecoveryCode> findByUserIdAndConsumedAtIsNull(Long userId);

    Optional<UserRecoveryCode> findByUserIdAndCodeHash(Long userId, String codeHash);

    @Modifying
    @Query("DELETE FROM UserRecoveryCode r WHERE r.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    long countByUserIdAndConsumedAtIsNull(Long userId);
}
