package com.medicore.repository;

import com.medicore.entity.PatientProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PatientProfileRepository extends JpaRepository<PatientProfile, Long> {
    Optional<PatientProfile> findByUser_Id(Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PatientProfile p where p.id = :id")
    Optional<PatientProfile> findByIdForUpdate(@Param("id") Long id);
}
