package com.medicore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicore.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ApiSecurityErrorWriter {
    private final ObjectMapper mapper;

    public ApiSecurityErrorWriter(ObjectMapper mapper) { this.mapper = mapper; }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String code, String message) throws IOException {
        Object correlation = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiErrorResponse(Instant.now(), status.value(), code,
                message, Map.of(), correlation == null ? "unknown" : correlation.toString()));
    }
}
