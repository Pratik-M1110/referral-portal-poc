package com.referralportal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

/**
 * A meet request that becomes a chat once accepted. {@code initiatedBy} tracks whether the seeker
 * requested it (uses the seeker's 3-request budget) or the referrer invited the seeker (does not).
 */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "seeker_id", nullable = false)
    private User seeker;

    @ManyToOne
    @JoinColumn(name = "referrer_id", nullable = false)
    private User referrer;

    /** 'seeker' or 'referrer'. */
    @Column(name = "initiated_by", nullable = false, length = 10)
    private String initiatedBy = "seeker";

    @Column(name = "first_message", length = 200)
    private String firstMessage;

    /** pending, accepted, declined or completed. */
    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "message_count", nullable = false)
    private short messageCount;

    @Column
    private Boolean happened;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "conversation")
    private List<Message> messages;

    public Long getId() {
        return id;
    }

    public User getSeeker() {
        return seeker;
    }

    public void setSeeker(User seeker) {
        this.seeker = seeker;
    }

    public User getReferrer() {
        return referrer;
    }

    public void setReferrer(User referrer) {
        this.referrer = referrer;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(String initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public String getFirstMessage() {
        return firstMessage;
    }

    public void setFirstMessage(String firstMessage) {
        this.firstMessage = firstMessage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public short getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(short messageCount) {
        this.messageCount = messageCount;
    }

    public Boolean getHappened() {
        return happened;
    }

    public void setHappened(Boolean happened) {
        this.happened = happened;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<Message> getMessages() {
        return messages;
    }
}
