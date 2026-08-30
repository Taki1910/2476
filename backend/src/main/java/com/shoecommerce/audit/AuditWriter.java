package com.shoecommerce.audit;

import java.time.Clock;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import com.shoecommerce.identity.SessionPrincipal;
import com.shoecommerce.platform.api.CorrelationIdFilter;

@Component
public class AuditWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void append(
            SessionPrincipal actor,
            String action,
            String resourceType,
            UUID resourcePublicId,
            Long branchId,
            Long locationId,
            Map<String, ?> details) {
        append(actor, "HUMAN", action, resourceType, resourcePublicId, branchId, locationId, details);
    }

    public void appendIntegration(
            SessionPrincipal actor,
            String action,
            String resourceType,
            UUID resourcePublicId,
            Long branchId,
            Long locationId,
            Map<String, ?> details) {
        append(actor, "INTEGRATION", action, resourceType, resourcePublicId, branchId, locationId, details);
    }

    public void appendIntegration(
            String provider,
            String action,
            String resourceType,
            UUID resourcePublicId,
            Long branchId,
            Long locationId,
            Map<String, ?> details) {
        append(null, "INTEGRATION", action, resourceType, resourcePublicId, branchId, locationId,
                Map.of("provider", provider, "evidence", details));
    }

    private void append(
            SessionPrincipal actor,
            String actorType,
            String action,
            String resourceType,
            UUID resourcePublicId,
            Long branchId,
            Long locationId,
            Map<String, ?> details) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO audit_event(
                        public_id, actor_type, actor_account_id, action, resource_type,
                        resource_public_id, branch_id, location_id, correlation_id,
                        occurred_at, result, details_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', ?)
                    """,
                    UUID.randomUUID(), actorType, actor == null ? null : actor.accountId(), action, resourceType,
                    resourcePublicId, branchId, locationId,
                    MDC.get(CorrelationIdFilter.MDC_KEY), Timestamp.from(clock.instant()),
                    objectMapper.writeValueAsString(details));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Audit details could not be serialized", exception);
        }
    }
}
