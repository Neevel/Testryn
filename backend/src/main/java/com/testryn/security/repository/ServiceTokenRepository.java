package com.testryn.security.repository;

import com.testryn.security.domain.ServiceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceTokenRepository extends JpaRepository<ServiceToken, UUID> {

    /** O(1) lookup by the token's non-secret public half (Abschnitt 19) -- never a
     * full-table scan. {@code lookup_id} is uniquely indexed. */
    Optional<ServiceToken> findByLookupId(String lookupId);

    List<ServiceToken> findAllByOrderByCreatedAtDesc();
}
