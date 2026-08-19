package com.chatapp.moderation.repository;

import com.chatapp.moderation.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {

    Optional<UserBlock> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    List<UserBlock> findAllByBlockerId(UUID blockerId);

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /**
     * True if either user has blocked the other - used to gate whether a
     * conversation/message can be created between the two.
     */
    default boolean existsEitherDirection(UUID userA, UUID userB) {
        return existsByBlockerIdAndBlockedId(userA, userB) || existsByBlockerIdAndBlockedId(userB, userA);
    }
}
