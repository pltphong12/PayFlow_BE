package com.payflow.user.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.payflow.user.entity.UserRole;
import com.payflow.user.entity.UserStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileResponse {
    private UUID id;
    private String email;
    private String fullName;
    private UserRole role;
    private UserStatus status;
    private Instant createdAt;    
}
