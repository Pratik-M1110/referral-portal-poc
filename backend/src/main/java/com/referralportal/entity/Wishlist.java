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
import java.time.Instant;
import java.util.Objects;

/** A seeker's wishlisted company; a seeker may wishlist at most 5 (enforced in app code). */
@Entity
@Table(name = "wishlists")
public class Wishlist {

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "seeker_id")
        private Long seekerId;

        @Column(name = "company_id")
        private Long companyId;

        public Id() {
        }

        public Id(Long seekerId, Long companyId) {
            this.seekerId = seekerId;
            this.companyId = companyId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Id id)) {
                return false;
            }
            return Objects.equals(seekerId, id.seekerId) && Objects.equals(companyId, id.companyId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(seekerId, companyId);
        }
    }

    @EmbeddedId
    private Id id = new Id();

    @ManyToOne
    @MapsId("seekerId")
    @JoinColumn(name = "seeker_id")
    private User seeker;

    @ManyToOne
    @MapsId("companyId")
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Id getId() {
        return id;
    }

    public User getSeeker() {
        return seeker;
    }

    public void setSeeker(User seeker) {
        this.seeker = seeker;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
