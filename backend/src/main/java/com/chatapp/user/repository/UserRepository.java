package com.chatapp.user.repository;

import com.chatapp.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            SELECT u FROM User u
            WHERE u.status = com.chatapp.user.entity.UserStatus.ACTIVE
              AND u.id <> :excludeUserId
              AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%')))
            ORDER BY u.username
            """)
    List<User> searchActiveUsers(@Param("query") String query, @Param("excludeUserId") UUID excludeUserId);
}
