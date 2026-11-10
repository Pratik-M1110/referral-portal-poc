package com.referralportal.repository.jpa;

import com.referralportal.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {

    /** Newest first; relies on the (user_id, read_at) / created_at ordering for the bell list. */
    List<Notification> findByUser_IdOrderByCreatedAtDesc(long userId);

    /** Unread count for the bell badge; served by the idx_user_unread index. */
    long countByUser_IdAndReadAtIsNull(long userId);

    /** Scoped lookup so a user can only act on their own notifications. */
    Optional<Notification> findByIdAndUser_Id(long id, long userId);

    /** Marks every still-unread notification for the user as read in one statement. Returns rows updated. */
    @Modifying
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") long userId, @Param("now") Instant now);
}
