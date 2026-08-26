package com.smartjobtracker.repository;

import com.smartjobtracker.model.NotificationDelivery;
import com.smartjobtracker.model.NotificationDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    Optional<NotificationDelivery> findByDedupeKey(String dedupeKey);
    Optional<NotificationDelivery> findByProviderMessageId(String providerMessageId);
    List<NotificationDelivery> findByStatusInAndNextAttemptAtBefore(List<NotificationDeliveryStatus> statuses, OffsetDateTime before);
    List<NotificationDelivery> findByUserIdOrderByCreatedAtDesc(Long userId);
}