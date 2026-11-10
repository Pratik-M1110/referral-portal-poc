package com.referralportal.service;

import com.referralportal.entity.Notification;
import com.referralportal.repository.NotificationRepository;
import com.referralportal.repository.jpa.NotificationJpaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.referralportal.dto.NotificationDtos.NotificationView;

/** Read and mark-read access to a user's own in-app notifications. Creation stays in ReferralNotificationService. */
@Service
public class NotificationService {

    @Autowired
    private UserService users;
    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private NotificationJpaRepository notificationJpa;

    public List<NotificationView> list(Authentication auth) {
        return notifications.findForUser(users.current(auth).id());
    }

    public long unreadCount(Authentication auth) {
        return notifications.unreadCount(users.current(auth).id());
    }

    @Transactional
    public void markRead(Authentication auth, long id) {
        long userId = users.current(auth).id();
        Notification notification = notificationJpa.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found."));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationJpa.save(notification);
        }
    }

    @Transactional
    public void markAllRead(Authentication auth) {
        notificationJpa.markAllRead(users.current(auth).id(), Instant.now());
    }
}
