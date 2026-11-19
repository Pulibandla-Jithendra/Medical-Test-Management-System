package com.medicore.repository;

import com.medicore.entity.ReportRecord;
import com.medicore.entity.ReportState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRecordRepository extends JpaRepository<ReportRecord, Long> {
    List<ReportRecord> findAllByRequest_IdOrderByReportVersionDesc(Long requestId);
    Optional<ReportRecord> findFirstByRequest_IdAndReportStateOrderByReportVersionDesc(Long requestId, ReportState reportState);
    List<ReportRecord> findAllByRequest_Patient_User_IdOrderByReportDateDesc(Long userId);
    List<ReportRecord> findAllByGeneratedBy_IdOrderByReportDateDesc(Long userId);
    List<ReportRecord> findAllByRequest_IdInOrderByReportDateDesc(List<Long> requestIds);
}
