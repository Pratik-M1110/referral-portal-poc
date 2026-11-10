package com.referralportal.controller;

import com.referralportal.service.NotificationService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.referralportal.dto.NotificationDtos.NotificationView;

@RestController
@RequestMapping("/api")
public class NotificationController {

    @Autowired
    private NotificationService notifications;

    /** In-app notifications for the signed-in person, newest first. */
    @GetMapping("/notifications")
    public List<NotificationView> list(Authentication auth) {
        return notifications.list(auth);
    }

    /** Unread count for the bell badge. */
    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("count", notifications.unreadCount(auth));
    }

    /** Marks one notification read. Idempotent; 404 if it is not the caller's. */
    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable long id, Authentication auth) {
        notifications.markRead(auth, id);
    }

    /** Marks all of the caller's unread notifications read. */
    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(Authentication auth) {
        notifications.markAllRead(auth);
    }
}
