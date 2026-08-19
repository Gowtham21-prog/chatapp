package com.chatapp.conversation.repository;

import com.chatapp.conversation.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

    List<ConversationParticipant> findAllByConversationId(UUID conversationId);

    Optional<ConversationParticipant> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    @Query("""
            SELECT p.userId FROM ConversationParticipant p
            WHERE p.conversation.id = :conversationId AND p.userId <> :excludeUserId
            """)
    List<UUID> findOtherParticipantIds(@Param("conversationId") UUID conversationId,
                                        @Param("excludeUserId") UUID excludeUserId);
}
