package com.medicore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicore.config.CorrelationIdFilter;
import com.medicore.entity.AuditLogEntry;
import com.medicore.entity.UserAccount;
import com.medicore.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {
    private final AuditLogRepository auditLogs;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogs, ObjectMapper objectMapper) {
        this.auditLogs = auditLogs;
        this.objectMapper = objectMapper;
    }

    public void record(String action, UserAccount actor, UserAccount subject, String entityType,
                       Object entityId, Object before, Object after) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.action = action;
        entry.actor = actor;
        entry.subject = subject;
        entry.entityType = entityType;
        entry.entityId = entityId == null ? null : entityId.toString();
        entry.occurredAt = LocalDateTime.now(ZoneOffset.UTC);
        entry.beforeData = json(before);
        entry.afterData = json(after);
        entry.correlationId = correlationId();
        auditLogs.save(entry);
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Audit metadata could not be serialized."); }
    }

    private String correlationId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return null;
        HttpServletRequest request = attributes.getRequest();
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value == null ? null : value.toString();
    }
}
