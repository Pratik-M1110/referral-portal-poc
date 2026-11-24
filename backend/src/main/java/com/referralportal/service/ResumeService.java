package com.referralportal.service;

import com.referralportal.dto.ResumeDtos.ResumeInfo;
import com.referralportal.entity.ReferralRequestResume;
import com.referralportal.entity.ResumeFile;
import com.referralportal.repository.jpa.ResumeFileJpaRepository;
import com.referralportal.repository.jpa.ReferralRequestResponseJpaRepository;
import com.referralportal.repository.jpa.ReferralRequestResumeJpaRepository;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Upload, read, and remove the signed-in seeker's own resume. One PDF per user, stored in MySQL. */
@Service
public class ResumeService {

    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final String PDF = "application/pdf";

    @Autowired
    private UserService users;
    @Autowired
    private ResumeFileJpaRepository resumes;
    @Autowired
    private ReferralRequestResponseJpaRepository responses;
    @Autowired
    private ReferralRequestResumeJpaRepository requestResumes;

    @Transactional
    public void upload(Authentication auth, MultipartFile file) {
        long userId = users.current(auth).id();
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a PDF file to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Resume must be 5 MB or smaller.");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file.");
        }
        if (!looksLikePdf(data)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF resumes are accepted.");
        }
        Instant uploadedAt = Instant.now();
        resumes.upsert(userId, safeName(file.getOriginalFilename()), PDF, data.length,
                sha256(data), data, uploadedAt);
    }

    public ReferralRequestResume downloadForReferrer(Authentication auth, long requestId) {
        long referrerId = users.current(auth).id();
        var response = responses.findByRequestIdAndReferrerId(requestId, referrerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You were not invited to this referral request."));
        var request = response.getRequest();
        boolean pendingAndOpen = "pending".equals(response.getStatus())
            && "open".equals(request.getStatus())
            && request.getExpiresAt().isAfter(Instant.now());
        boolean accepted = Set.of("accepted", "submitted", "confirmed").contains(response.getStatus());
        if (!pendingAndOpen && !accepted) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "The resume is available only while your response is pending or accepted.");
        }
        if (!request.isShareSeekerResume()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No resume was included with this request.");
        }
        return requestResumes.findById(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No resume was included with this request."));
    }

    public ResumeFile download(Authentication auth) {
        long userId = users.current(auth).id();
        return resumes.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No resume uploaded yet."));
    }

    public Optional<ResumeInfo> info(Authentication auth) {
        long userId = users.current(auth).id();
        return resumes.findMetaByUser_Id(userId).map(meta -> new ResumeInfo(
                meta.getFilename(), meta.getContentType(), meta.getSizeBytes(), meta.getUploadedAt().toString()));
    }

    @Transactional
    public void delete(Authentication auth) {
        long userId = users.current(auth).id();
        requestResumes.deleteBySeekerId(userId);
        resumes.deleteByUserId(userId);
    }

    /** Magic-byte check: a real PDF starts with "%PDF". Content-Type from the browser is not trusted. */
    private static boolean looksLikePdf(byte[] data) {
        return data.length >= 5 && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F';
    }

    private static String safeName(String original) {
        if (original == null || original.isBlank()) {
            return "resume.pdf";
        }
        String name = original.replaceAll(".*[/\\\\]", "").trim();
        if (name.isBlank()) {
            name = "resume.pdf";
        }
        if (!name.toLowerCase().endsWith(".pdf")) {
            name = name + ".pdf";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
