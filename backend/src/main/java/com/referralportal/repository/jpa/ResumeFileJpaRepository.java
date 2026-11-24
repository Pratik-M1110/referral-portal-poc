package com.referralportal.repository.jpa;

import com.referralportal.entity.ResumeFile;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeFileJpaRepository extends JpaRepository<ResumeFile, Long> {

    /** Metadata only — a closed projection so the resume bytes are not loaded to render the UI. */
    Optional<ResumeMetaView> findMetaByUser_Id(long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ResumeFile r where r.userId = :userId")
    void deleteByUserId(@Param("userId") long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO resume_files
              (user_id, filename, content_type, size_bytes, sha256, data, uploaded_at)
            VALUES
              (:userId, :filename, :contentType, :sizeBytes, :sha256, :data, :uploadedAt)
            ON DUPLICATE KEY UPDATE
              filename = VALUES(filename),
              content_type = VALUES(content_type),
              size_bytes = VALUES(size_bytes),
              sha256 = VALUES(sha256),
              data = VALUES(data),
              uploaded_at = VALUES(uploaded_at)
            """, nativeQuery = true)
    void upsert(@Param("userId") long userId,
                @Param("filename") String filename,
                @Param("contentType") String contentType,
                @Param("sizeBytes") long sizeBytes,
                @Param("sha256") String sha256,
                @Param("data") byte[] data,
                @Param("uploadedAt") Instant uploadedAt);

    interface ResumeMetaView {
        String getFilename();

        String getContentType();

        long getSizeBytes();

        Instant getUploadedAt();
    }
}
