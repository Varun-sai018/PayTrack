import { queryOptions } from "@tanstack/react-query";

import { supabase } from "@/integrations/supabase/client";
import type { Database } from "@/integrations/supabase/types";

export type Profile = Database["public"]["Tables"]["profiles"]["Row"];
export type Wallet = Database["public"]["Tables"]["wallets"]["Row"];
export type LedgerEntry = Database["public"]["Tables"]["ledger_entries"]["Row"];
export type Transaction = Database["public"]["Tables"]["transactions"]["Row"];
export type Expense = Database["public"]["Tables"]["expenses"]["Row"];

async function currentUserId(): Promise<string> {
  const { data } = await supabase.auth.getSession();
  const id = data.session?.user.id;
  if (!id) throw new Error("Not signed in");
  return id;
}

export const profileQuery = queryOptions({
  queryKey: ["profile"],
  queryFn: async (): Promise<Profile | null> => {
    const id = await currentUserId();
    const { data, error } = await supabase.from("profiles").select("*").eq("id", id).maybeSingle();
    if (error) throw error;
    return data;
  },
});

export const walletQuery = queryOptions({
  queryKey: ["wallet"],
  queryFn: async (): Promise<Wallet | null> => {
    const id = await currentUserId();
    const { data, error } = await supabase.from("wallets").select("*").eq("user_id", id).maybeSingle();
    if (error) throw error;
    return data;
  },
});

export const ledgerQuery = queryOptions({
  queryKey: ["ledger"],
  queryFn: async (): Promise<LedgerEntry[]> => {
    const { data, error } = await supabase
      .from("ledger_entries")
      .select("*")
      .order("created_at", { ascending: false })
      .limit(10);
    if (error) throw error;
    return data;
  },
});

export const transactionsQuery = (limit: number) =>
  queryOptions({
    queryKey: ["transactions", limit],
    queryFn: async (): Promise<{ rows: Transaction[]; total: number }> => {
      const { data, error, count } = await supabase
        .from("transactions")
        .select("*", { count: "exact" })
        .order("created_at", { ascending: false })
        .limit(limit);
      if (error) throw error;
      return { rows: data, total: count ?? data.length };
    },
  });

export const expensesQuery = queryOptions({
  queryKey: ["expenses"],
  queryFn: async (): Promise<Expense[]> => {
    const { data, error } = await supabase
      .from("expenses")
      .select("*")
      .order("created_at", { ascending: false });
    if (error) throw error;
    return data;
  },
});

/** Money moves touch the wallet, ledger, transactions and expenses at once. */
export const MONEY_QUERY_KEYS = [["wallet"], ["ledger"], ["transactions"], ["expenses"]] as const;
