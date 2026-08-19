package com.chatapp.message.repository;

import com.chatapp.message.entity.MessageReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, UUID> {

    Optional<MessageReceipt> findByMessageIdAndUserId(UUID messageId, UUID userId);

    List<MessageReceipt> findAllByMessageIdIn(List<UUID> messageIds);

    @Query("""
            SELECT r FROM MessageReceipt r
            WHERE r.userId = :userId AND r.messageId IN :messageIds
            """)
    List<MessageReceipt> findAllForUserAndMessages(@Param("userId") UUID userId,
                                                     @Param("messageIds") List<UUID> messageIds);
}
