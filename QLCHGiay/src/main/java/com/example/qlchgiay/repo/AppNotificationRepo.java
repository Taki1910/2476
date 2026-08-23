package com.example.qlchgiay.repo;

import com.example.qlchgiay.model.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppNotificationRepo extends JpaRepository<AppNotification, Integer> {
    List<AppNotification> findTop20ByRecipientIdOrderByCreatedAtDesc(Integer recipientId);
    long countByRecipientIdAndReadAtIsNull(Integer recipientId);
    Optional<AppNotification> findByIdAndRecipientId(Integer id, Integer recipientId);
    boolean existsByRecipientIdAndDedupeKey(Integer recipientId, String dedupeKey);
}
