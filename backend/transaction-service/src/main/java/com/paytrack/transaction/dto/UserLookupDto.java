package com.paytrack.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLookupDto {

    private String maskedName;
    private String upiId;
    private String userId;
    private String accountStatus;
    private String kycStatus;
}