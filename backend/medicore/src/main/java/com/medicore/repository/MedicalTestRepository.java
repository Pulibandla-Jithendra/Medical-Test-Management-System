package com.medicore.repository;

import com.medicore.entity.MedicalTest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalTestRepository extends JpaRepository<MedicalTest, Long> {
    List<MedicalTest> findAllByActiveTrueOrderByNameAsc();
}
