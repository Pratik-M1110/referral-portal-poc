package com.referralportal.chat;

import com.referralportal.company.RequestBudget;
import com.referralportal.user.CurrentUser;
import com.referralportal.user.UserService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import static com.referralportal.chat.ConversationDtos.ConversationView;
import static com.referralportal.chat.ConversationDtos.MeetRequest;
import static com.referralportal.chat.ConversationDtos.MessageInput;
import static com.referralportal.chat.ConversationDtos.MessageView;
import static com.referralportal.chat.ConversationDtos.ThreadView;

@Service
class ConversationService {

    static final int MESSAGE_CAP = 20;

    private final UserService users;
    private final ConversationRepository conversations;
    private final TransactionTemplate transactions;
    private final RequestBudget budget;
    private final boolean seedDemo;

    ConversationService(UserService users, ConversationRepository conversations, TransactionTemplate transactions,
            RequestBudget budget, @Value("${app.seed-demo:false}") boolean seedDemo) {
        this.users = users;
        this.conversations = conversations;
        this.transactions = transactions;
        this.budget = budget;
        this.seedDemo = seedDemo;
    }

    List<ConversationView> list(Authentication auth) {
        CurrentUser user = users.current(auth);
        return conversations.findForUser(user.id());
    }

    ThreadView thread(Authentication auth, long conversationId) {
        CurrentUser user = users.current(auth);
        ConversationView conversation = load(conversationId, user.id());
        List<MessageView> messages = conversations.findMessages(conversationId, user.id());
        return new ThreadView(conversation, MESSAGE_CAP, messages);
    }

    Map<String, Object> send(Authentication auth, long conversationId, MessageInput input) {
        CurrentUser user = users.current(auth);
        ConversationView conversation = load(conversationId, user.id());
        if (!"accepted".equals(conversation.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chat opens after the request is accepted.");
        }
        if (conversations.isBlockedPair(user.id(), conversation.otherId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This chat is not available.");
        }
        String body = input.body().trim();
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write a message first.");
        }
        int used = Objects.requireNonNull(transactions.execute(status -> {
            int updated = conversations.incrementMessageCount(conversationId, MESSAGE_CAP);
            if (updated != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This chat has used all " + MESSAGE_CAP + " messages.");
            }
            conversations.insertMessage(conversationId, user.id(), body);
            return conversations.messageCount(conversationId);
        }));
        return Map.of("messageCount", used, "messageCap", MESSAGE_CAP);
    }

    Map<String, Object> request(Authentication auth, MeetRequest input) {
        CurrentUser seeker = users.current(auth);
        if (input.referrerId() == seeker.id()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot request yourself.");
        }
        ConversationRepository.ReferrerTarget target = conversations.findOpenReferrer(input.referrerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This person is not open to meet."));
        boolean demoReferrer = "demo".equals(target.authProvider());
        if (conversations.hasVerifiedCompany(seeker.id(), target.companyId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot request a meeting at your own company.");
        }
        if (conversations.isBlockedPair(seeker.id(), input.referrerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot send a request to this person.");
        }
        if (conversations.countAcceptedForReferrer(input.referrerId()) >= target.slotCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This person is fully booked.");
        }
        if (budget.left(seeker.id()) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have used all " + RequestBudget.REQUESTS_MAX + " meet requests.");
        }
        try {
            long id = Objects.requireNonNull(transactions.execute(status -> {
                long conversationId = conversations.createConversation(seeker.id(), input.referrerId(), input.note().trim());
                if (seedDemo && demoReferrer) {
                    conversations.acceptDemoConversation(conversationId, input.referrerId(), seeker.name());
                }
                return conversationId;
            }));
            return Map.of("id", id, "requestsLeft", budget.left(seeker.id()));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a conversation with this person.");
        }
    }

    Map<String, Object> accept(Authentication auth, long conversationId) {
        CurrentUser user = users.current(auth);
        ConversationView conversation = decidable(user, conversationId);
        if ("seeker".equals(conversation.initiatedBy()) && "referrer".equals(conversation.myRole())) {
            long used = conversations.countAcceptedForReferrer(user.id());
            int capacity = conversations.slotCapacity(user.id());
            if (used >= capacity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You are fully booked.");
            }
        }
        conversations.accept(conversationId);
        return Map.of("status", "accepted");
    }

    Map<String, Object> decline(Authentication auth, long conversationId) {
        CurrentUser user = users.current(auth);
        decidable(user, conversationId);
        conversations.decline(conversationId);
        return Map.of("status", "declined");
    }

    private ConversationView decidable(CurrentUser user, long conversationId) {
        ConversationView conversation = load(conversationId, user.id());
        boolean startedByMe = conversation.initiatedBy().equals(conversation.myRole());
        if (startedByMe || !"pending".equals(conversation.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nothing to accept or decline here.");
        }
        return conversation;
    }

    private ConversationView load(long conversationId, long userId) {
        return conversations.findForParticipant(conversationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
    }
}
