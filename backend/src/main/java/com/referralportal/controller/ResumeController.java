package com.referralportal.controller;

import com.referralportal.dto.ResumeDtos.ResumeInfo;
import com.referralportal.entity.ResumeFile;
import com.referralportal.service.ResumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** The signed-in seeker's own resume. One PDF per user. */
@RestController
@RequestMapping("/api/me/resume")
public class ResumeController {

    @Autowired
    private ResumeService resumes;

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void upload(@RequestParam(value = "file", required = false) MultipartFile file, Authentication auth) {
        resumes.upload(auth, file);
    }

    /** Metadata for the current resume, or 204 when none is uploaded. */
    @GetMapping("/info")
    public ResponseEntity<ResumeInfo> info(Authentication auth) {
        return resumes.info(auth).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public ResponseEntity<byte[]> download(Authentication auth) {
        ResumeFile resume = resumes.download(auth);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(resume.getFilename()).build().toString())
                .body(resume.getData());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth) {
        resumes.delete(auth);
    }
}
