package com.medicore.repository;

import com.medicore.entity.TestResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {
    List<TestResult> findAllByRequest_IdOrderByVersionNoAsc(Long requestId);
    Optional<TestResult> findFirstByRequest_IdOrderByVersionNoDesc(Long requestId);
}
