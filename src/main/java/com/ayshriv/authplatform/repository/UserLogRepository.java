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
}
