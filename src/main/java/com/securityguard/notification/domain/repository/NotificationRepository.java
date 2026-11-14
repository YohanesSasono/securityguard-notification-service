package com.securityguard.notification.domain.repository;

import com.securityguard.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByCompanyIdAndRecipientUserIdOrderByCreatedAtDesc(UUID companyId, UUID recipientUserId, Pageable pageable);

    Optional<Notification> findByDedupeKey(String dedupeKey);

    /** Plain, non-locking read: just the candidate ids due for an attempt. */
    @Query("SELECT n.id FROM Notification n WHERE n.status = 'PENDING' AND n.nextAttemptAt <= :now ORDER BY n.createdAt ASC")
    List<UUID> findDueIds(@Param("now") Instant now, org.springframework.data.domain.Pageable pageable);

    /**
     * Claims exactly one row for exclusive processing by this transaction.
     * SKIP LOCKED means a concurrent dispatcher (another thread, or another
     * app instance) racing for the same id simply gets nothing back instead
     * of blocking - the correct, database-backed way to let multiple
     * dispatchers compete for work without double-processing the same
     * notification (ARCHITECTURE_V2.txt: "concurrent processing").
     */
    @Query(value = "SELECT * FROM notifications WHERE id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<Notification> claimForUpdate(@Param("id") UUID id);
}
