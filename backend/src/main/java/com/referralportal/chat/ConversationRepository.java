package com.referralportal.chat;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import static com.referralportal.chat.ConversationDtos.ConversationView;
import static com.referralportal.chat.ConversationDtos.MessageView;

@Repository
class ConversationRepository {

    private static final String SELECT = """
            SELECT c.id, c.seeker_id, c.referrer_id, c.status, c.initiated_by, c.message_count, c.first_message,
                   su.name AS seeker_name, ru.name AS referrer_name, rp.job_title, co.name AS company_name,
                   (SELECT m.body FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1) AS last_message
            FROM conversations c
            JOIN users su ON su.id = c.seeker_id
            JOIN users ru ON ru.id = c.referrer_id
            LEFT JOIN referrer_profiles rp ON rp.user_id = c.referrer_id
            LEFT JOIN companies co ON co.id = rp.company_id""";

    private final JdbcClient jdbc;

    ConversationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<ConversationView> findForUser(long userId) {
        return jdbc.sql(SELECT + " WHERE c.seeker_id = :me OR c.referrer_id = :me ORDER BY c.id DESC")
                .param("me", userId).query((rs, n) -> view(rs, userId)).list();
    }

    Optional<ConversationView> findForParticipant(long conversationId, long userId) {
        return jdbc.sql(SELECT + " WHERE c.id = :id AND (c.seeker_id = :me OR c.referrer_id = :me)")
                .param("id", conversationId).param("me", userId)
                .query((rs, n) -> view(rs, userId)).optional();
    }

    List<MessageView> findMessages(long conversationId, long userId) {
        return jdbc.sql("SELECT id, sender_id, body, sent_at FROM messages WHERE conversation_id = ? ORDER BY id")
                .param(conversationId)
                .query((rs, n) -> new MessageView(rs.getLong("id"), rs.getLong("sender_id"),
                        rs.getLong("sender_id") == userId, rs.getString("body"),
                        rs.getTimestamp("sent_at").toInstant().toString()))
                .list();
    }

    Optional<ReferrerTarget> findOpenReferrer(long userId) {
        return jdbc.sql("""
                SELECT rp.company_id, rp.slot_capacity, u.auth_provider FROM referrer_profiles rp JOIN users u ON u.id = rp.user_id
                WHERE rp.user_id = ? AND rp.verified_at IS NOT NULL AND rp.is_referrer = TRUE AND rp.open_to_meet = TRUE""")
                .param(userId)
                .query((rs, n) -> new ReferrerTarget(rs.getLong("company_id"), rs.getInt("slot_capacity"),
                        rs.getString("auth_provider")))
                .optional();
    }

    boolean hasVerifiedCompany(long userId, long companyId) {
        return jdbc.sql("SELECT COUNT(*) FROM referrer_profiles WHERE user_id = ? AND company_id = ? AND verified_at IS NOT NULL")
                .params(userId, companyId).query(Long.class).single() > 0;
    }

    boolean isBlockedPair(long firstUserId, long secondUserId) {
        return jdbc.sql("SELECT COUNT(*) FROM reports_blocks WHERE type = 'block' AND ((reporter_id = ? AND target_id = ?) OR (reporter_id = ? AND target_id = ?))")
                .params(firstUserId, secondUserId, secondUserId, firstUserId).query(Long.class).single() > 0;
    }

    long countAcceptedForReferrer(long referrerId) {
        return jdbc.sql("SELECT COUNT(*) FROM conversations WHERE referrer_id = ? AND status IN ('accepted','completed')")
                .param(referrerId).query(Long.class).single();
    }

    int slotCapacity(long referrerId) {
        return jdbc.sql("SELECT slot_capacity FROM referrer_profiles WHERE user_id = ?")
                .param(referrerId).query(Integer.class).single();
    }

    int incrementMessageCount(long conversationId, int messageCap) {
        return jdbc.sql("UPDATE conversations SET message_count = message_count + 1 WHERE id = ? AND status = 'accepted' AND message_count < ?")
                .params(conversationId, messageCap).update();
    }

    void insertMessage(long conversationId, long senderId, String body) {
        jdbc.sql("INSERT INTO messages (conversation_id, sender_id, body) VALUES (?, ?, ?)")
                .params(conversationId, senderId, body).update();
    }

    int messageCount(long conversationId) {
        return jdbc.sql("SELECT message_count FROM conversations WHERE id = ?").param(conversationId)
                .query(Integer.class).single();
    }

    long createConversation(long seekerId, long referrerId, String note) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO conversations (seeker_id, referrer_id, initiated_by, first_message) VALUES (?, ?, 'seeker', ?)")
                .params(seekerId, referrerId, note).update(keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    void acceptDemoConversation(long conversationId, long referrerId, String seekerName) {
        jdbc.sql("UPDATE conversations SET status = 'accepted', message_count = 1 WHERE id = ?")
                .param(conversationId).update();
        String trimmed = seekerName == null ? "" : seekerName.trim();
        int space = trimmed.indexOf(' ');
        String firstName = space > 0 ? trimmed.substring(0, space) : trimmed;
        insertMessage(conversationId, referrerId, "Hi " + firstName + "! Happy to chat. Where are you at the venue?");
    }

    void accept(long conversationId) {
        jdbc.sql("UPDATE conversations SET status = 'accepted' WHERE id = ? AND status = 'pending'")
                .param(conversationId).update();
    }

    void decline(long conversationId) {
        jdbc.sql("UPDATE conversations SET status = 'declined' WHERE id = ? AND status = 'pending'")
                .param(conversationId).update();
    }

    private static ConversationView view(ResultSet rs, long userId) throws SQLException {
        boolean isSeeker = rs.getLong("seeker_id") == userId;
        return new ConversationView(rs.getLong("id"), rs.getString("status"), rs.getString("initiated_by"),
                isSeeker ? "seeker" : "referrer", rs.getInt("message_count"),
                isSeeker ? rs.getLong("referrer_id") : rs.getLong("seeker_id"),
                isSeeker ? rs.getString("referrer_name") : rs.getString("seeker_name"),
                isSeeker ? rs.getString("job_title") : null, rs.getString("company_name"),
                rs.getString("first_message"), rs.getString("last_message"));
    }

    record ReferrerTarget(long companyId, int slotCapacity, String authProvider) {}
}
