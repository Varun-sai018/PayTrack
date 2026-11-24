// PayTrack business rules and formatting, shared by the UI.
// The same limits are enforced again inside the database money functions.

export const TRANSFER_MIN = 1;
export const TRANSFER_MAX = 10000;
export const RECHARGE_MIN = 10;
export const RECHARGE_MAX = 2000;
export const TOPUP_MIN = 1;
export const UPI_SUFFIX = "@paytrack";
export const OPERATORS = ["JIO", "AIRTEL", "VI", "BSNL"] as const;
export type Operator = (typeof OPERATORS)[number];

export const CATEGORY_LABELS = {
  FOOD: "Food",
  TRANSPORT: "Travel",
  HOUSING: "Housing",
  ENTERTAINMENT: "Entertainment",
  UTILITIES: "Bills & Recharges",
  HEALTHCARE: "Health",
  SHOPPING: "Shopping",
  EDUCATION: "Education",
  OTHER: "Others",
} as const;
export type Category = keyof typeof CATEGORY_LABELS;
export const CATEGORIES = Object.keys(CATEGORY_LABELS) as Category[];

export function categoryLabel(category: string | null | undefined): string {
  if (!category) return "Others";
  return (CATEGORY_LABELS as Record<string, string>)[category] ?? category;
}

const inr = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatINR(amount: number | string | null | undefined): string {
  if (amount === null || amount === undefined || amount === "") return "₹0.00";
  const num = typeof amount === "string" ? parseFloat(amount) : amount;
  if (Number.isNaN(num)) return "₹0.00";
  return inr.format(num);
}

export function upiFromPhone(phone: string | null | undefined): string | null {
  return phone ? `${phone}${UPI_SUFFIX}` : null;
}

/** Indian mobile number used for the UPI ID: 10 digits, starting 6–9. */
export function isValidIndianMobile(phone: string): boolean {
  return /^[6-9]\d{9}$/.test(phone);
}

/** Each validator returns an error message, or null when the input is valid. */
export function validateTransfer(receiverUpiId: string, amount: number): string | null {
  if (!amount || Number.isNaN(amount) || amount < TRANSFER_MIN || amount > TRANSFER_MAX) {
    return "Amount must be between ₹1.00 and ₹10,000.00";
  }
  if (!receiverUpiId.trim().toLowerCase().endsWith(UPI_SUFFIX)) {
    return "UPI ID must end with @paytrack";
  }
  return null;
}

export function validateRecharge(mobile: string, amount: number): string | null {
  if (!amount || Number.isNaN(amount) || amount < RECHARGE_MIN || amount > RECHARGE_MAX) {
    return "Recharge amount must be between ₹10.00 and ₹2,000.00";
  }
  if (!/^\d{10}$/.test(mobile)) {
    return "Mobile number must be exactly 10 digits";
  }
  return null;
}

export function validateTopup(amount: number): string | null {
  if (!amount || Number.isNaN(amount) || amount < TOPUP_MIN) {
    return "Minimum top-up amount is ₹1.00";
  }
  return null;
}

export function validatePassword(password: string, confirm: string): string | null {
  if (password !== confirm) return "Passwords do not match";
  if (password.length < 8) return "Password must be at least 8 characters";
  return null;
}

export function formatDate(value: string | null | undefined, withTime = false): string {
  if (!value) return "-";
  return new Date(value).toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
    ...(withTime ? { hour: "2-digit", minute: "2-digit" } : {}),
  });
}

export function friendlyError(error: unknown, fallback: string): string {
  if (error && typeof error === "object" && "message" in error) {
    const msg = String((error as { message: unknown }).message);
    if (msg) return msg;
  }
  return fallback;
}

type TxnLike = {
  user_id: string;
  type: string;
  receiver_upi_id: string | null;
  sender_upi_id: string | null;
  operator: string | null;
  mobile_number: string | null;
  description: string | null;
};

/** Money leaves the wallet when the signed-in user started the transaction. */
export function isDebitFor(txn: Pick<TxnLike, "user_id">, userId: string): boolean {
  return txn.user_id === userId;
}

export function txnCounterparty(txn: TxnLike, userId: string): string {
  if (!isDebitFor(txn, userId)) return txn.sender_upi_id ?? "PayTrack user";
  if (txn.type === "RECHARGE") return `${txn.operator ?? ""} • ${txn.mobile_number ?? ""}`.trim();
  return txn.receiver_upi_id ?? txn.description ?? "Transfer";
}
