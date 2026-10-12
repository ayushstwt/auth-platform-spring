package com.ayshriv.authplatform.repository;

import com.ayshriv.authplatform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByPhoneE164(String phoneE164);

    boolean existsByPhoneE164(String phoneE164);

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE " +
            "(:query IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
            "(:status IS NULL OR u.status = :status)")
    org.springframework.data.domain.Page<User> searchUsers(
            @org.springframework.data.repository.query.Param("query") String query,
            @org.springframework.data.repository.query.Param("status") com.ayshriv.authplatform.entity.UserStatus status,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM users WHERE is_deleted = true AND deleted_at < :purgeThreshold", nativeQuery = true)
    java.util.List<User> findSoftDeletedUsersBefore(@org.springframework.data.repository.query.Param("purgeThreshold") java.time.Instant purgeThreshold);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "DELETE FROM users WHERE is_deleted = true AND deleted_at < :purgeThreshold", nativeQuery = true)
    int hardPurgeUsersBefore(@org.springframework.data.repository.query.Param("purgeThreshold") java.time.Instant purgeThreshold);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "DELETE FROM users", nativeQuery = true)
    void hardDeleteAll();
}
