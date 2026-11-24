import { queryOptions } from "@tanstack/react-query";
import {
  api,
  type Expense,
  type LedgerEntry,
  type Profile,
  type Transaction,
  type Wallet,
} from "./api";

export type { Profile, Wallet, LedgerEntry, Transaction, Expense };

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

/** Money moves touch the wallet, ledger, transactions and expenses at once. */
export const MONEY_QUERY_KEYS = [["wallet"], ["ledger"], ["transactions"], ["expenses"]] as const;
