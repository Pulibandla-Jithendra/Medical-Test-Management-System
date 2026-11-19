package com.medicore.repository;

import com.medicore.entity.AuditLogEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {
    List<AuditLogEntry> findTop500ByOrderByOccurredAtDesc();
}
