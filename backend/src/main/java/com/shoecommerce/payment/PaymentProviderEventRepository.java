package com.shoecommerce.payment;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentProviderEventRepository extends JpaRepository<PaymentProviderEvent, Long> {
    @Query(value = "SELECT events.* FROM payment_provider_event events WITH (UPDLOCK, HOLDLOCK) WHERE events.provider_account_public_id = :providerId AND events.provider_event_id = :eventId", nativeQuery = true)
    Optional<PaymentProviderEvent> findScopedForUpdate(@Param("providerId") UUID providerId, @Param("eventId") String eventId);
}
