package com.medicore.service;

import com.medicore.dto.HealthResponse;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    public HealthResponse getStatus() {
        return new HealthResponse("UP", "MediCore");
    }
}
