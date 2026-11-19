package com.medicore.repository;

import com.medicore.entity.CorrectionStatus;
import com.medicore.entity.ResultCorrectionRequest;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultCorrectionRequestRepository extends JpaRepository<ResultCorrectionRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ResultCorrectionRequest c where c.id = :id")
    Optional<ResultCorrectionRequest> findByIdForUpdate(@Param("id") Long id);

    List<ResultCorrectionRequest> findAllByStatusOrderByRequestedAtAsc(CorrectionStatus status);
    List<ResultCorrectionRequest> findAllByRequestedBy_IdOrderByRequestedAtDesc(Long userId);
    boolean existsByRequest_IdAndTargetResult_IdAndStatus(Long requestId, Long resultId, CorrectionStatus status);
}
