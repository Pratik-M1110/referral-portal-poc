package com.referralportal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/** A referrer's Save or Pass decision on a candidate, with an optional private note only they can see. */
@Entity
@Table(name = "reviews")
public class Review {

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "referrer_id")
        private Long referrerId;

        @Column(name = "candidate_id")
        private Long candidateId;

        public Id() {
        }

        public Id(Long referrerId, Long candidateId) {
            this.referrerId = referrerId;
            this.candidateId = candidateId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Id id)) {
                return false;
            }
            return Objects.equals(referrerId, id.referrerId) && Objects.equals(candidateId, id.candidateId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(referrerId, candidateId);
        }
    }

    @EmbeddedId
    private Id id = new Id();

    @ManyToOne
    @MapsId("referrerId")
    @JoinColumn(name = "referrer_id")
    private User referrer;

    @ManyToOne
    @MapsId("candidateId")
    @JoinColumn(name = "candidate_id")
    private User candidate;

    /** 'save' or 'pass'. */
    @Column(nullable = false, length = 10)
    private String action;

    @Column(name = "private_note", columnDefinition = "text")
    private String privateNote;

    public Id getId() {
        return id;
    }

    public User getReferrer() {
        return referrer;
    }

    public void setReferrer(User referrer) {
        this.referrer = referrer;
    }

    public User getCandidate() {
        return candidate;
    }

    public void setCandidate(User candidate) {
        this.candidate = candidate;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getPrivateNote() {
        return privateNote;
    }

    public void setPrivateNote(String privateNote) {
        this.privateNote = privateNote;
    }
}
