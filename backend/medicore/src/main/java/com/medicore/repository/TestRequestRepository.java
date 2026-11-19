package com.medicore.repository;

import com.medicore.entity.TestRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestRequestRepository extends JpaRepository<TestRequest, Long> {
    List<TestRequest> findAllByPatient_User_IdOrderByRequestDateDesc(Long userId);
    List<TestRequest> findAllByDoctor_User_IdOrderByRequestDateDesc(Long userId);
    List<TestRequest> findAllByAssignedTechnician_IdOrderByScheduledDateAscScheduledTimeAsc(Long userId);
    List<TestRequest> findAllByOrderByRequestDateDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TestRequest r where r.id = :id")
    Optional<TestRequest> findByIdForUpdate(@Param("id") Long id);
}
