package com.referralportal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** A referrer scanning an attendee's QR badge (or a booth QR for company interest), logged for stats. */
@Entity
@Table(name = "scans")
public class Scan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "scanner_id", nullable = false)
    private User scanner;

    @ManyToOne
    @JoinColumn(name = "scanned_user_id")
    private User scannedUser;

    @ManyToOne
    @JoinColumn(name = "scanned_company_id")
    private Company scannedCompany;

    /** referrer_scan or company_interest. */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(name = "synced_at")
    private Instant syncedAt;

    public Long getId() {
        return id;
    }

    public User getScanner() {
        return scanner;
    }

    public void setScanner(User scanner) {
        this.scanner = scanner;
    }

    public User getScannedUser() {
        return scannedUser;
    }

    public void setScannedUser(User scannedUser) {
        this.scannedUser = scannedUser;
    }

    public Company getScannedCompany() {
        return scannedCompany;
    }

    public void setScannedCompany(Company scannedCompany) {
        this.scannedCompany = scannedCompany;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(Instant scannedAt) {
        this.scannedAt = scannedAt;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Instant syncedAt) {
        this.syncedAt = syncedAt;
    }
}
