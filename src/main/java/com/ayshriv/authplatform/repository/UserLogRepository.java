package com.ayshriv.authplatform.repository;

import com.ayshriv.authplatform.dto.UserLogResponse;
import com.ayshriv.authplatform.entity.UserLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLogRepository extends JpaRepository<UserLog, Long> {

    @Query("""
           SELECT new com.ayshriv.authplatform.dto.UserLogResponse(
               l.id, l.userId, l.action, l.subAction, l.createdOn)
           FROM UserLog l
           WHERE l.userId = :userId
           ORDER BY l.createdOn DESC, l.id DESC
           """)
    Page<UserLogResponse> findResponsesByUserId(@Param("userId") Long userId, Pageable pageable);

    Page<UserLog> findByUserIdOrderByCreatedOnDescIdDesc(Long userId, Pageable pageable);

    @Query("SELECT l FROM UserLog l WHERE " +
           "(:userId IS NULL OR l.userId = :userId) AND " +
           "(:action IS NULL OR l.action = :action) AND " +
           "(:startDate IS NULL OR l.createdOn >= :startDate) AND " +
           "(:endDate IS NULL OR l.createdOn <= :endDate) " +
           "ORDER BY l.createdOn DESC, l.id DESC")
    Page<UserLog> searchLogs(@Param("userId") Long userId,
                            @Param("action") com.ayshriv.authplatform.entity.LogAction action,
                            @Param("startDate") java.time.Instant startDate,
                            @Param("endDate") java.time.Instant endDate,
                            Pageable pageable);
}
