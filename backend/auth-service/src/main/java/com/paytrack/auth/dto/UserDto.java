package com.paytrack.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private Long id;
    private String userId;
    private String email;
    private String fullName;
    private String role;
    private String phone;
    private String upiId;
    private String kycStatus;
    private String accountStatus;
    private boolean active;
    private LocalDateTime createdAt;
}
