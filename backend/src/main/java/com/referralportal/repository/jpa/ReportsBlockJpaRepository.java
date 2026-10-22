package com.referralportal.repository.jpa;

import com.referralportal.entity.ReportsBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository backing {@link com.referralportal.repository.ConversationRepository}. */
public interface ReportsBlockJpaRepository extends JpaRepository<ReportsBlock, Long> {

    @Query("select count(b) > 0 from ReportsBlock b where b.type = 'block' and "
            + "((b.reporter.id = :a and b.target.id = :b) or (b.reporter.id = :b and b.target.id = :a))")
    boolean existsBlockedPair(@Param("a") long firstUserId, @Param("b") long secondUserId);
}
