package com.payflow.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.payflow.user.entity.UserRole;
import com.payflow.user.entity.UserStatus;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RegisterResponse {
    private UUID id;
    private String email;
    private String fullName;
    private UserRole role;
    private UserStatus status;
    private Instant createdAt;
}
