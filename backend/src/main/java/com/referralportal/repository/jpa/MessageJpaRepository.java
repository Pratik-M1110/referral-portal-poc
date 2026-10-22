package com.referralportal.repository.jpa;

import com.referralportal.entity.Message;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository backing {@link com.referralportal.repository.ConversationRepository}. */
public interface MessageJpaRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderById(Long conversationId);

    Optional<Message> findFirstByConversationIdOrderByIdDesc(Long conversationId);
}
