package com.paytrack.transaction.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    private String fullName;
    private String upiId;
    @JsonAlias({"id", "userId"})
    private String userId;
    private String accountStatus;
    private String kycStatus;

    public String getDisplayName() {
        if (maskedName != null && !maskedName.isEmpty()) {
            return maskedName;
        }
        return fullName != null ? fullName : "User";
    }
}
