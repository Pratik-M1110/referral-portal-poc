package com.referralportal.repository;

import com.referralportal.entity.Company;
import com.referralportal.entity.Conversation;
import com.referralportal.entity.Message;
import com.referralportal.entity.ReferrerProfile;
import com.referralportal.entity.User;
import com.referralportal.repository.jpa.ConversationJpaRepository;
import com.referralportal.repository.jpa.MessageJpaRepository;
import com.referralportal.repository.jpa.ReferrerProfileJpaRepository;
import com.referralportal.repository.jpa.ReportsBlockJpaRepository;
import com.referralportal.repository.jpa.UserJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static com.referralportal.dto.ConversationDtos.ConversationView;
import static com.referralportal.dto.ConversationDtos.MessageView;

@Repository
public class ConversationRepository {

    private static final List<String> ACTIVE_STATUSES = List.of("accepted", "completed");

    @Autowired
    private ConversationJpaRepository conversations;
    @Autowired
    private MessageJpaRepository messages;
    @Autowired
    private ReferrerProfileJpaRepository referrerProfiles;
    @Autowired
    private ReportsBlockJpaRepository reportsBlocks;
    @Autowired
    private UserJpaRepository users;

    public List<ConversationView> findForUser(long userId) {
        return conversations.findBySeekerIdOrReferrerIdOrderByIdDesc(userId, userId).stream()
                .map(c -> view(c, userId)).toList();
    }

    public Optional<ConversationView> findForParticipant(long conversationId, long userId) {
        return conversations.findById(conversationId)
                .filter(c -> c.getSeeker().getId() == userId || c.getReferrer().getId() == userId)
                .map(c -> view(c, userId));
    }

    public List<MessageView> findMessages(long conversationId, long userId) {
        return messages.findByConversationIdOrderById(conversationId).stream()
                .map(m -> new MessageView(m.getId(), m.getSender().getId(), m.getSender().getId() == userId,
                        m.getBody(), m.getSentAt().toString()))
                .toList();
    }

    public Optional<ReferrerTarget> findOpenReferrer(long userId) {
        return referrerProfiles.findById(userId)
                .filter(rp -> rp.getVerifiedAt() != null && rp.isReferrer() && rp.isOpenToMeet())
                .map(rp -> new ReferrerTarget(rp.getCompany().getId(), rp.getSlotCapacity(), rp.getUser().getAuthProvider()));
    }

    public boolean hasVerifiedCompany(long userId, long companyId) {
        return referrerProfiles.findById(userId)
                .map(rp -> rp.getVerifiedAt() != null && rp.getCompany().getId() == companyId)
                .orElse(false);
    }

    public boolean isBlockedPair(long firstUserId, long secondUserId) {
        return reportsBlocks.existsBlockedPair(firstUserId, secondUserId);
    }

    public long countAcceptedForReferrer(long referrerId) {
        return conversations.countByReferrerIdAndStatusIn(referrerId, ACTIVE_STATUSES);
    }

    public int slotCapacity(long referrerId) {
        return referrerProfiles.findById(referrerId).map(rp -> (int) rp.getSlotCapacity())
                .orElseThrow(() -> new NoSuchElementException("No referrer profile for user " + referrerId));
    }

    public int incrementMessageCount(long conversationId, int messageCap) {
        return conversations.incrementMessageCount(conversationId, messageCap);
    }

    public void insertMessage(long conversationId, long senderId, String body) {
        Conversation conversation = conversations.getReferenceById(conversationId);
        User sender = users.getReferenceById(senderId);
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setBody(body);
        message.setSentAt(Instant.now());
        messages.save(message);
    }

    public int messageCount(long conversationId) {
        return conversations.findById(conversationId).map(Conversation::getMessageCount).map(Short::intValue)
                .orElseThrow(() -> new NoSuchElementException("No conversation " + conversationId));
    }

    public long createConversation(long seekerId, long referrerId, String note) {
        User seeker = users.getReferenceById(seekerId);
        User referrer = users.getReferenceById(referrerId);
        Conversation conversation = new Conversation();
        conversation.setSeeker(seeker);
        conversation.setReferrer(referrer);
        conversation.setInitiatedBy("seeker");
        conversation.setFirstMessage(note);
        conversation.setStatus("pending");
        conversation.setCreatedAt(Instant.now());
        conversation = conversations.save(conversation);
        return conversation.getId();
    }

    public void accept(long conversationId) {
        conversations.acceptPending(conversationId);
    }

    public void decline(long conversationId) {
        conversations.declinePending(conversationId);
    }

    private ConversationView view(Conversation c, long userId) {
        boolean isSeeker = c.getSeeker().getId() == userId;
        User other = isSeeker ? c.getReferrer() : c.getSeeker();
        String otherTitle = null;
        String companyName = null;
        if (isSeeker) {
            otherTitle = referrerProfiles.findById(other.getId()).map(ReferrerProfile::getJobTitle).orElse(null);
            companyName = referrerProfiles.findById(other.getId()).map(ReferrerProfile::getCompany)
                    .map(Company::getName).orElse(null);
        } else {
            companyName = referrerProfiles.findById(userId).map(ReferrerProfile::getCompany)
                    .map(Company::getName).orElse(null);
        }
        String lastMessage = messages.findFirstByConversationIdOrderByIdDesc(c.getId())
                .map(Message::getBody).orElse(null);
        return new ConversationView(c.getId(), c.getStatus(), c.getInitiatedBy(), isSeeker ? "seeker" : "referrer",
                c.getMessageCount(), other.getId(), other.getName(), otherTitle, companyName, c.getFirstMessage(),
                lastMessage);
    }

    public record ReferrerTarget(long companyId, int slotCapacity, String authProvider) {}
}
