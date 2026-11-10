package com.referralportal.dto;

import java.util.Map;

public final class NotificationDtos {

    private NotificationDtos() {}

    /** One in-app notification as shown in the bell list. {@code payload} is the stored JSON parsed into a map. */
    public record NotificationView(long id, String type, Map<String, Object> payload, boolean read, String createdAt) {}
}
