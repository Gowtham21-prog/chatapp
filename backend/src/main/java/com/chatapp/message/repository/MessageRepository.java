package com.chatapp.message.repository;

import com.chatapp.message.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findAllByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    @Query("""
            SELECT m FROM Message m
            WHERE m.conversationId = :conversationId
              AND m.deletedAt IS NULL
              AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY m.createdAt DESC
            """)
    Page<Message> searchInConversation(@Param("conversationId") UUID conversationId,
                                        @Param("query") String query, Pageable pageable);

    @Query("""
            SELECT m FROM Message m
            JOIN ConversationParticipant p ON p.conversation.id = m.conversationId
            WHERE p.userId = :userId
              AND m.deletedAt IS NULL
              AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%'))
            ORDER BY m.createdAt DESC
            """)
    Page<Message> searchAcrossUserConversations(@Param("userId") UUID userId,
                                                  @Param("query") String query, Pageable pageable);

    long countByConversationIdAndSenderIdNotAndStatusNot(UUID conversationId, UUID senderId,
                                                           com.chatapp.message.entity.MessageStatus status);
}
