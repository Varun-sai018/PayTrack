package com.paytrack.auth.repository;

import com.paytrack.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByUserIdAndRevokedFalse(String userId);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.expiresAt < :now AND rt.revoked = false")
    List<RefreshToken> findExpiredTokens(@Param("now") LocalDateTime now);

    void deleteByUserId(String userId);
}