package com.fintrack.repository;

import com.fintrack.entity.AccountToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {

    // Fetch the user eagerly; consumers act on it outside this transaction.
    @Query("SELECT t FROM AccountToken t JOIN FETCH t.user WHERE t.tokenHash = :tokenHash AND t.purpose = :purpose")
    Optional<AccountToken> findByTokenHashAndPurpose(
            @Param("tokenHash") String tokenHash, @Param("purpose") AccountToken.Purpose purpose);

    @Modifying
    @Query("UPDATE AccountToken t SET t.usedAt = :now "
            + "WHERE t.user.id = :userId AND t.purpose = :purpose AND t.usedAt IS NULL")
    int invalidateAllForUser(@Param("userId") Long userId,
                             @Param("purpose") AccountToken.Purpose purpose,
                             @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM AccountToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
