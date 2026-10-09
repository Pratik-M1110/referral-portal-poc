package com.referralportal.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ConversationDtos {

    private ConversationDtos() {}

    public record ConversationView(long id, String status, String initiatedBy, String myRole, int messageCount,
            long otherId, String otherName, String otherTitle, String companyName, String note, String lastMessage) {}

    public record MessageView(long id, long senderId, boolean mine, String body, String sentAt) {}

    public record ThreadView(ConversationView conversation, int messageCap, List<MessageView> messages) {}

    public record MeetRequest(long referrerId, @NotBlank @Size(max = 200) String note) {}

    public record MessageInput(@NotBlank @Size(max = 500) String body) {}
}
