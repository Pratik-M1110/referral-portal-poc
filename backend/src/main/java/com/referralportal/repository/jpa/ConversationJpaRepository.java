package com.referralportal.repository.jpa;

import com.referralportal.entity.Conversation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository backing {@link com.referralportal.repository.ConversationRepository}. */
public interface ConversationJpaRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findBySeekerIdOrReferrerIdOrderByIdDesc(Long seekerId, Long referrerId);

    Optional<Conversation> findBySeekerIdAndReferrerId(Long seekerId, Long referrerId);

    long countByReferrerIdAndStatusIn(Long referrerId, List<String> statuses);

    long countBySeekerIdAndInitiatedBy(Long seekerId, String initiatedBy);

    /** Atomic conditional update enforcing the 20-message cap (rule 6); returns affected-row count. */
    @Modifying
    @Query("update Conversation c set c.messageCount = c.messageCount + 1 "
            + "where c.id = :id and c.status = 'accepted' and c.messageCount < :cap")
    int incrementMessageCount(@Param("id") long conversationId, @Param("cap") int messageCap);

    @Modifying
    @Query("update Conversation c set c.status = 'accepted' where c.id = :id and c.status = 'pending'")
    int acceptPending(@Param("id") long conversationId);

    @Modifying
    @Query("update Conversation c set c.status = 'declined' where c.id = :id and c.status = 'pending'")
    int declinePending(@Param("id") long conversationId);

    @Modifying
    @Query("update Conversation c set c.status = 'accepted', c.messageCount = 1 where c.id = :id")
    int acceptDemo(@Param("id") long conversationId);

    @Modifying
    @Query("delete from Conversation c where c.seeker.id = :userId or c.referrer.id = :userId")
    void deleteBySeekerIdOrReferrerId(@Param("userId") long userId);
}
