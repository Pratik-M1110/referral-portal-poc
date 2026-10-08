package com.referralportal.chat;

import com.referralportal.company.RequestBudget;
import com.referralportal.user.CurrentUser;
import com.referralportal.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ConversationController {

    static final int MESSAGE_CAP = 20;

    record ConversationView(long id, String status, String initiatedBy, String myRole, int messageCount,
            long otherId, String otherName, String otherTitle, String companyName, String note, String lastMessage) {}

    record MessageView(long id, long senderId, boolean mine, String body, String sentAt) {}

    record ThreadView(ConversationView conversation, int messageCap, List<MessageView> messages) {}

    record MeetRequest(long referrerId, @NotBlank @Size(max = 200) String note) {}

    record MessageInput(@NotBlank @Size(max = 500) String body) {}

    private final UserService users;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final RequestBudget budget;
    private final boolean seedDemo;

    ConversationController(UserService users, JdbcClient jdbc, TransactionTemplate tx, RequestBudget budget,
            @Value("${app.seed-demo:false}") boolean seedDemo) {
        this.users = users;
        this.jdbc = jdbc;
        this.tx = tx;
        this.budget = budget;
        this.seedDemo = seedDemo;
    }

    @GetMapping("/api/conversations")
    List<ConversationView> list(Authentication auth) {
        CurrentUser me = users.current(auth);
        return jdbc.sql(SELECT + " WHERE c.seeker_id = :me OR c.referrer_id = :me ORDER BY c.id DESC")
                .param("me", me.id())
                .query((rs, n) -> view(rs, me.id()))
                .list();
    }

    @GetMapping("/api/conversations/{id}/messages")
    ThreadView thread(Authentication auth, @PathVariable long id) {
        CurrentUser me = users.current(auth);
        ConversationView conversation = load(id, me.id());
        List<MessageView> messages = jdbc.sql("SELECT id, sender_id, body, sent_at FROM messages WHERE conversation_id = ? ORDER BY id")
                .param(id)
                .query((rs, n) -> new MessageView(rs.getLong("id"), rs.getLong("sender_id"),
                        rs.getLong("sender_id") == me.id(), rs.getString("body"), rs.getTimestamp("sent_at").toInstant().toString()))
                .list();
        return new ThreadView(conversation, MESSAGE_CAP, messages);
    }

    /** All 20 messages are shared by both people; the guarded UPDATE and the insert commit together. */
    @PostMapping("/api/conversations/{id}/messages")
    Map<String, Object> send(Authentication auth, @PathVariable long id, @Valid @RequestBody MessageInput in) {
        CurrentUser me = users.current(auth);
        ConversationView conversation = load(id, me.id());
        if (!"accepted".equals(conversation.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chat opens after the request is accepted.");
        }
        if (blocked(me.id(), conversation.otherId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This chat is not available.");
        }
        String body = in.body().trim();
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write a message first.");
        }
        int used = Objects.requireNonNull(tx.execute(status -> {
            int updated = jdbc.sql("UPDATE conversations SET message_count = message_count + 1 WHERE id = ? AND status = 'accepted' AND message_count < ?")
                    .params(id, MESSAGE_CAP).update();
            if (updated != 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This chat has used all " + MESSAGE_CAP + " messages.");
            }
            jdbc.sql("INSERT INTO messages (conversation_id, sender_id, body) VALUES (?, ?, ?)").params(id, me.id(), body).update();
            return jdbc.sql("SELECT message_count FROM conversations WHERE id = ?").param(id).query(Integer.class).single();
        }));
        return Map.of("messageCount", used, "messageCap", MESSAGE_CAP);
    }

    @PostMapping("/api/conversations")
    Map<String, Object> request(Authentication auth, @Valid @RequestBody MeetRequest in) {
        CurrentUser me = users.current(auth);
        if (in.referrerId() == me.id()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot request yourself.");
        }
        var target = jdbc.sql("""
                SELECT rp.company_id, rp.slot_capacity, u.auth_provider FROM referrer_profiles rp JOIN users u ON u.id = rp.user_id
                WHERE rp.user_id = ? AND rp.verified_at IS NOT NULL AND rp.is_referrer = TRUE AND rp.open_to_meet = TRUE""")
                .param(in.referrerId())
                .query((rs, n) -> new Object[] {rs.getLong("company_id"), rs.getInt("slot_capacity"), rs.getString("auth_provider")})
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This person is not open to meet."));
        long companyId = (Long) target[0];
        int capacity = (Integer) target[1];
        boolean demoReferrer = "demo".equals(target[2]);
        long ownCompany = jdbc.sql("SELECT COUNT(*) FROM referrer_profiles WHERE user_id = ? AND company_id = ? AND verified_at IS NOT NULL")
                .params(me.id(), companyId).query(Long.class).single();
        if (ownCompany > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot request a meeting at your own company.");
        }
        if (blocked(me.id(), in.referrerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot send a request to this person.");
        }
        long used = jdbc.sql("SELECT COUNT(*) FROM conversations WHERE referrer_id = ? AND status IN ('accepted','completed')")
                .param(in.referrerId()).query(Long.class).single();
        if (used >= capacity) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This person is fully booked.");
        }
        if (budget.left(me.id()) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have used all " + RequestBudget.REQUESTS_MAX + " meet requests.");
        }
        try {
            long id = Objects.requireNonNull(tx.execute(status -> {
                KeyHolder keys = new GeneratedKeyHolder();
                jdbc.sql("INSERT INTO conversations (seeker_id, referrer_id, initiated_by, first_message) VALUES (?, ?, 'seeker', ?)")
                        .params(me.id(), in.referrerId(), in.note().trim()).update(keys);
                long conversationId = Objects.requireNonNull(keys.getKey()).longValue();
                if (seedDemo && demoReferrer) {
                    // Demo-only: sample referrers are not real accounts, so they accept and say hello right away.
                    jdbc.sql("UPDATE conversations SET status = 'accepted', message_count = 1 WHERE id = ?").param(conversationId).update();
                    jdbc.sql("INSERT INTO messages (conversation_id, sender_id, body) VALUES (?, ?, ?)")
                            .params(conversationId, in.referrerId(), "Hi " + firstName(me.name()) + "! Happy to chat. Where are you at the venue?").update();
                }
                return conversationId;
            }));
            return Map.of("id", id, "requestsLeft", budget.left(me.id()));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a conversation with this person.");
        }
    }

    @PostMapping("/api/conversations/{id}/accept")
    Map<String, Object> accept(Authentication auth, @PathVariable long id) {
        ConversationView c = decidable(auth, id);
        if ("seeker".equals(c.initiatedBy()) && "referrer".equals(c.myRole())) {
            long used = jdbc.sql("SELECT COUNT(*) FROM conversations WHERE referrer_id = (SELECT referrer_id FROM conversations WHERE id = ?) AND status IN ('accepted','completed')")
                    .param(id).query(Long.class).single();
            int capacity = jdbc.sql("SELECT slot_capacity FROM referrer_profiles WHERE user_id = (SELECT referrer_id FROM conversations WHERE id = ?)")
                    .param(id).query(Integer.class).single();
            if (used >= capacity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You are fully booked.");
            }
        }
        jdbc.sql("UPDATE conversations SET status = 'accepted' WHERE id = ? AND status = 'pending'").param(id).update();
        return Map.of("status", "accepted");
    }

    @PostMapping("/api/conversations/{id}/decline")
    Map<String, Object> decline(Authentication auth, @PathVariable long id) {
        decidable(auth, id);
        jdbc.sql("UPDATE conversations SET status = 'declined' WHERE id = ? AND status = 'pending'").param(id).update();
        return Map.of("status", "declined");
    }

    /** Only the person who did not start a pending conversation may accept or decline it. */
    private ConversationView decidable(Authentication auth, long id) {
        CurrentUser me = users.current(auth);
        ConversationView c = load(id, me.id());
        boolean iStarted = c.initiatedBy().equals(c.myRole());
        if (iStarted || !"pending".equals(c.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Nothing to accept or decline here.");
        }
        return c;
    }

    private boolean blocked(long a, long b) {
        return jdbc.sql("SELECT COUNT(*) FROM reports_blocks WHERE type = 'block' AND ((reporter_id = ? AND target_id = ?) OR (reporter_id = ? AND target_id = ?))")
                .params(a, b, b, a).query(Long.class).single() > 0;
    }

    private ConversationView load(long id, long meId) {
        return jdbc.sql(SELECT + " WHERE c.id = :id AND (c.seeker_id = :me OR c.referrer_id = :me)")
                .param("id", id).param("me", meId)
                .query((rs, n) -> view(rs, meId))
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
    }

    private static ConversationView view(java.sql.ResultSet rs, long meId) throws java.sql.SQLException {
        boolean imSeeker = rs.getLong("seeker_id") == meId;
        return new ConversationView(rs.getLong("id"), rs.getString("status"), rs.getString("initiated_by"),
                imSeeker ? "seeker" : "referrer", rs.getInt("message_count"),
                imSeeker ? rs.getLong("referrer_id") : rs.getLong("seeker_id"),
                imSeeker ? rs.getString("referrer_name") : rs.getString("seeker_name"),
                imSeeker ? rs.getString("job_title") : null,
                rs.getString("company_name"), rs.getString("first_message"), rs.getString("last_message"));
    }

    private static String firstName(String name) {
        String trimmed = name == null ? "" : name.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private static final String SELECT = """
            SELECT c.id, c.seeker_id, c.referrer_id, c.status, c.initiated_by, c.message_count, c.first_message,
                   su.name AS seeker_name, ru.name AS referrer_name, rp.job_title, co.name AS company_name,
                   (SELECT m.body FROM messages m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1) AS last_message
            FROM conversations c
            JOIN users su ON su.id = c.seeker_id
            JOIN users ru ON ru.id = c.referrer_id
            LEFT JOIN referrer_profiles rp ON rp.user_id = c.referrer_id
            LEFT JOIN companies co ON co.id = rp.company_id""";
}
