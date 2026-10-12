package com.ayshriv.authplatform.repository;

import com.ayshriv.authplatform.entity.FederatedIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FederatedIdentityRepository extends JpaRepository<FederatedIdentity, Long> {

    Optional<FederatedIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);

    List<FederatedIdentity> findByUserId(Long userId);

    Optional<FederatedIdentity> findByUserIdAndProvider(Long userId, String provider);

    boolean existsByUserIdAndProvider(Long userId, String provider);

    boolean existsByProviderAndProviderSubject(String provider, String providerSubject);

    long countByUserId(Long userId);
}
