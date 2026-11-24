package com.paytrack.transaction.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Random;

@Component
@Slf4j
public class DummyRechargeOperator implements RechargeOperator {

    private final Random random = new Random();

    @Override
    public RechargeResult executeRecharge(String mobileNumber, String operator, BigDecimal amount) {
        if (mobileNumber != null && mobileNumber.endsWith("0000")) {
            log.info("Recharge declined for number ending in 0000: {}", mobileNumber);
            return new RechargeResult(false, null, "OPERATOR_DECLINED");
        }

        String operatorRef = "OP" + String.format("%06d", random.nextInt(999999) + 1);
        log.info("Recharge succeeded for {} with operator {}: {}", mobileNumber, operator, operatorRef);
        return new RechargeResult(true, operatorRef, null);
    }
}
