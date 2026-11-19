package com.medicore.repository;

import com.medicore.entity.DoctorPatientAssignment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DoctorPatientAssignmentRepository extends JpaRepository<DoctorPatientAssignment, Long> {
    boolean existsByIdAndDoctor_User_IdAndPatient_User_IdAndRevokedAtIsNull(Long id, Long doctorUserId, Long patientUserId);
    List<DoctorPatientAssignment> findAllByPatient_User_IdOrderByAssignedAtDesc(Long patientUserId);
    List<DoctorPatientAssignment> findAllByDoctor_User_IdAndRevokedAtIsNullOrderByAssignedAtDesc(Long doctorUserId);
    boolean existsByDoctor_IdAndPatient_IdAndRevokedAtIsNull(Long doctorId, Long patientId);
    java.util.Optional<DoctorPatientAssignment> findFirstByDoctor_IdAndPatient_IdAndRevokedAtIsNullOrderByAssignedAtDesc(Long doctorId, Long patientId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DoctorPatientAssignment a where a.id = :id")
    java.util.Optional<DoctorPatientAssignment> findByIdForUpdate(@Param("id") Long id);
}
