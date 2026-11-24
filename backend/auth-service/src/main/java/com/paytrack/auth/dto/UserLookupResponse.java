package com.paytrack.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLookupResponse {

    private String userId;
    private String maskedName;
    private String upiId;
    private String accountStatus;
    private String kycStatus;
}