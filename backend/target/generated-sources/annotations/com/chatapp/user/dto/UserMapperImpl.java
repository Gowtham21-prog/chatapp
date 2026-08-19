package com.chatapp.user.dto;

import com.chatapp.user.entity.User;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-08-19T14:09:48+0530",
    comments = "version: 1.6.0, compiler: javac, environment: Java 21.0.11 (Ubuntu)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserProfileResponse toProfileResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UUID id = null;
        String username = null;
        String displayName = null;
        String avatarUrl = null;
        String bio = null;
        Instant createdAt = null;

        id = user.getId();
        username = user.getUsername();
        displayName = user.getDisplayName();
        avatarUrl = user.getAvatarUrl();
        bio = user.getBio();
        createdAt = user.getCreatedAt();

        UserProfileResponse userProfileResponse = new UserProfileResponse( id, username, displayName, avatarUrl, bio, createdAt );

        return userProfileResponse;
    }
}
