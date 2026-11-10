package com.referralportal.repository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.referralportal.entity.Notification;
import com.referralportal.repository.jpa.NotificationJpaRepository;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static com.referralportal.dto.NotificationDtos.NotificationView;

@Repository
public class NotificationRepository {

    @Autowired
    private NotificationJpaRepository notifications;
    @Autowired
    private ObjectMapper objectMapper;

    public List<NotificationView> findForUser(long userId) {
        return notifications.findByUser_IdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
    }

    public long unreadCount(long userId) {
        return notifications.countByUser_IdAndReadAtIsNull(userId);
    }

    private NotificationView view(Notification n) {
        return new NotificationView(n.getId(), n.getType(), parse(n.getPayload()),
                n.getReadAt() != null, n.getCreatedAt().toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, Map.class);
        } catch (JacksonException exception) {
            return Map.of();
        }
    }
}
