package com.paytrack.expense.event;

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
}
