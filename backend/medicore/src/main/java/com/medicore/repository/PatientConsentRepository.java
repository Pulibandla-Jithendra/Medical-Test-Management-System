package com.medicore.repository;

import com.medicore.entity.PatientConsent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientConsentRepository extends JpaRepository<PatientConsent, Long> {
    boolean existsByAssignment_IdAndRevokedAtIsNull(Long assignmentId);
    Optional<PatientConsent> findFirstByAssignment_IdAndRevokedAtIsNull(Long assignmentId);
    List<PatientConsent> findAllByAssignment_IdIn(List<Long> assignmentIds);
}
