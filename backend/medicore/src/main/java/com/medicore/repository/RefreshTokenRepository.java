package com.medicore.repository;

import com.medicore.entity.RefreshTokenRecord;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenRecord, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenRecord t where t.tokenHash = :hash")
    Optional<RefreshTokenRecord> findByHashForUpdate(@Param("hash") String hash);

    boolean existsByFamilyIdAndUser_IdAndRevokedAtIsNullAndExpiresAtAfter(String familyId, Long userId, LocalDateTime now);
    List<RefreshTokenRecord> findAllByFamilyIdAndRevokedAtIsNull(String familyId);
    List<RefreshTokenRecord> findAllByUser_IdAndRevokedAtIsNull(Long userId);

    @Modifying
    @Query("update RefreshTokenRecord t set t.revokedAt = :now where t.familyId = :family and t.revokedAt is null")
    int revokeFamily(@Param("family") String familyId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update RefreshTokenRecord t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
