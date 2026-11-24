import { queryOptions } from "@tanstack/react-query";
import {
  api,
  type AdminUser,
  type Expense,
  type KycItem,
  type LedgerEntry,
  type Profile,
  type ServiceHealth,
  type Transaction,
  type TransactionSummary,
  type Wallet,
} from "./api";

export type { Profile, Wallet, LedgerEntry, Transaction, Expense, AdminUser, KycItem, TransactionSummary, ServiceHealth };

export const profileQuery = queryOptions({
  queryKey: ["profile"],
  queryFn: async (): Promise<Profile | null> => {
    return api.getCurrentUser();
  },
});

export const walletQuery = queryOptions({
  queryKey: ["wallet"],
  queryFn: async (): Promise<Wallet | null> => {
    return api.getWallet();
  },
});

export const ledgerQuery = queryOptions({
  queryKey: ["ledger"],
  queryFn: async (): Promise<LedgerEntry[]> => {
    return api.getStatement();
  },
});

export const transactionsQuery = (limit: number) =>
  queryOptions({
    queryKey: ["transactions", limit],
    queryFn: async (): Promise<{ rows: Transaction[]; total: number }> => {
      return api.getTransactions(limit);
    },
  });

export const expensesQuery = queryOptions({
  queryKey: ["expenses"],
  queryFn: async (): Promise<Expense[]> => {
    return api.getExpenses();
  },
});

export const adminSummaryQuery = queryOptions({
  queryKey: ["admin-summary"],
  queryFn: async (): Promise<TransactionSummary> => {
    return api.getAdminTransactionSummary();
  },
});

export const adminUsersQuery = (status?: string) =>
  queryOptions({
    queryKey: ["admin-users", status],
    queryFn: async (): Promise<AdminUser[]> => {
      return api.getAdminUsers({ status });
    },
  });

export const adminKycQuery = (status?: string) =>
  queryOptions({
    queryKey: ["admin-kyc", status],
    queryFn: async (): Promise<KycItem[]> => {
      return api.getAdminKyc({ status });
    },
  });

export const adminTransactionsQuery = (filters?: { status?: string; type?: string }) =>
  queryOptions({
    queryKey: ["admin-transactions", filters?.status, filters?.type],
    queryFn: async (): Promise<{ rows: Transaction[]; total: number }> => {
      return api.getAdminAllTransactions(filters);
    },
  });

export const adminHealthQuery = queryOptions({
  queryKey: ["admin-health"],
  queryFn: async (): Promise<ServiceHealth[]> => {
    return api.checkServicesHealth();
  },
  refetchInterval: 15000, // live auto-refresh every 15s
});

/** Money moves touch the wallet, ledger, transactions and expenses at once. */
export const MONEY_QUERY_KEYS = [["wallet"], ["ledger"], ["transactions"], ["expenses"]] as const;
