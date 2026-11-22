package com.paytrack.auth.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {

    private String userId;
    private String email;
    private String fullName;
    private String role;
    private LocalDateTime timestamp;

    public static UserRegisteredEvent fromUser(String userId, String email, String fullName, String role) {
        return UserRegisteredEvent.builder()
                .userId(userId)
                .email(email)
                .fullName(fullName)
                .role(role)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
