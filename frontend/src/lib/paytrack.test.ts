import { describe, expect, it } from "vitest";
import {
  formatINR,
  isDebitFor,
  txnCounterparty,
  isValidIndianMobile,
  upiFromPhone,
  validatePassword,
  validateRecharge,
  validateTopup,
  validateTransfer,
} from "./paytrack";

describe("UPI transfer limits", () => {
  it("allows ₹1 to ₹10,000 to an @paytrack UPI ID", () => {
    expect(validateTransfer("9876543210@paytrack", 1)).toBeNull();
    expect(validateTransfer("9876543210@paytrack", 10000)).toBeNull();
  });
  it("rejects amounts below ₹1 or above ₹10,000", () => {
    expect(validateTransfer("9876543210@paytrack", 0.5)).not.toBeNull();
    expect(validateTransfer("9876543210@paytrack", 10000.01)).not.toBeNull();
  });
  it("rejects UPI IDs that are not @paytrack", () => {
    expect(validateTransfer("someone@okbank", 100)).toBe("UPI ID must end with @paytrack");
  });
});

describe("Mobile recharge limits", () => {
  it("allows ₹10 to ₹2,000 for a 10-digit number", () => {
    expect(validateRecharge("9876543210", 10)).toBeNull();
    expect(validateRecharge("9876543210", 2000)).toBeNull();
  });
  it("rejects amounts outside ₹10 to ₹2,000", () => {
    expect(validateRecharge("9876543210", 9)).not.toBeNull();
    expect(validateRecharge("9876543210", 2001)).not.toBeNull();
  });
  it("requires exactly 10 digits", () => {
    expect(validateRecharge("98765", 199)).toBe("Mobile number must be exactly 10 digits");
  });
});

describe("Wallet top-up", () => {
  it("requires at least ₹1", () => {
    expect(validateTopup(1)).toBeNull();
    expect(validateTopup(0.99)).toBe("Minimum top-up amount is ₹1.00");
  });
});

describe("Sign-up rules", () => {
  it("accepts Indian mobile numbers starting 6-9", () => {
    expect(isValidIndianMobile("6123456789")).toBe(true);
    expect(isValidIndianMobile("5123456789")).toBe(false);
    expect(isValidIndianMobile("987654321")).toBe(false);
  });
  it("builds the UPI ID as mobile@paytrack", () => {
    expect(upiFromPhone("9876543210")).toBe("9876543210@paytrack");
  });
  it("requires matching passwords of at least 8 characters", () => {
    expect(validatePassword("short", "short")).toBe("Password must be at least 8 characters");
    expect(validatePassword("longenough", "different")).toBe("Passwords do not match");
    expect(validatePassword("longenough", "longenough")).toBeNull();
  });
});

describe("INR formatting", () => {
  it("uses Indian digit grouping", () => {
    expect(formatINR(123456.5)).toBe("₹1,23,456.50");
  });
});

describe("Transaction direction", () => {
  const base = { type: "TRANSFER", receiver_upi_id: "9000000002@paytrack", sender_upi_id: "9000000001@paytrack", operator: null, mobile_number: null, description: null };
  it("is a debit for the sender and a credit for the receiver", () => {
    const txn = { ...base, user_id: "sender" };
    expect(isDebitFor(txn, "sender")).toBe(true);
    expect(isDebitFor(txn, "receiver")).toBe(false);
  });
  it("shows the other party's UPI ID", () => {
    const txn = { ...base, user_id: "sender" };
    expect(txnCounterparty(txn, "sender")).toBe("9000000002@paytrack");
    expect(txnCounterparty(txn, "receiver")).toBe("9000000001@paytrack");
  });
});
