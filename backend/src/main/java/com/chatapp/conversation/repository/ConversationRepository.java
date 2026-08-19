package com.chatapp.conversation.repository;

import com.chatapp.conversation.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByDirectPairKey(String directPairKey);

    @Query("""
            SELECT DISTINCT c FROM Conversation c
            JOIN c.participants p
            WHERE p.userId = :userId
            ORDER BY c.updatedAt DESC
            """)
    List<Conversation> findAllForUser(@Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(p) > 0 FROM ConversationParticipant p
            WHERE p.conversation.id = :conversationId AND p.userId = :userId
            """)
    boolean isParticipant(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);
}
