package com.paytrack.transaction.service;

import java.math.BigDecimal;

public interface RechargeOperator {
    RechargeResult executeRecharge(String mobileNumber, String operator, BigDecimal amount);

    record RechargeResult(boolean success, String operatorRef, String failureReason) {}
}
