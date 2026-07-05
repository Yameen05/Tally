package com.fintrack.repository;

import com.fintrack.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Fetch the user eagerly: callers hand it out past transaction boundaries.
    @Query("SELECT t FROM RefreshToken t JOIN FETCH t.user WHERE t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.user.id = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
