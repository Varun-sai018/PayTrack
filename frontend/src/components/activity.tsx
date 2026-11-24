import type { ReactNode } from "react";
import { ArrowDownLeft, ArrowUpRight, ShoppingBag, Smartphone } from "lucide-react";

import { StatusBadge } from "@/components/status-badge";
import { categoryLabel, formatDate, formatINR, isDebitFor, txnCounterparty, type Category } from "@/lib/paytrack";
import type { Expense, Transaction } from "@/lib/queries";
import { cn } from "@/lib/utils";

/** One colour per spending category, used by bars, dots and icons. */
const CATEGORY_TONE: Record<Category, string> = {
  FOOD: "bg-chart-3",
  TRANSPORT: "bg-chart-2",
  HOUSING: "bg-chart-5",
  ENTERTAINMENT: "bg-chart-4",
  UTILITIES: "bg-chart-1",
  HEALTHCARE: "bg-chart-6",
  SHOPPING: "bg-chart-4",
  EDUCATION: "bg-chart-2",
  OTHER: "bg-subtle-foreground",
};

export function categoryTone(category: string | null | undefined): string {
  return (CATEGORY_TONE as Record<string, string>)[category ?? ""] ?? "bg-subtle-foreground";
}

export function Amount({ value, sign, className }: { value: number | string; sign: "+" | "-" | null; className?: string | undefined }) {
  return (
    <span
      className={cn(
        "num whitespace-nowrap font-medium",
        sign === "+" ? "text-success" : "text-foreground",
        className,
      )}
    >
      {sign === "+" ? "+" : sign === "-" ? "−" : ""}
      {formatINR(value)}
    </span>
  );
}

export function TxnIcon({ txn, userId }: { txn: Pick<Transaction, "type" | "user_id">; userId: string }) {
  const debit = isDebitFor(txn, userId);
  const Icon = txn.type === "RECHARGE" ? Smartphone : debit ? ArrowUpRight : ArrowDownLeft;
  return (
    <span
      className={cn(
        "flex h-10 w-10 shrink-0 items-center justify-center rounded-full",
        debit ? "bg-surface text-foreground" : "bg-success/12 text-success",
      )}
    >
      <Icon className="h-[18px] w-[18px]" aria-hidden="true" />
    </span>
  );
}

export function TxnRow({ txn, userId }: { txn: Transaction; userId: string }) {
  const debit = isDebitFor(txn, userId);
  const failed = txn.status === "FAILED";
  const done = txn.status === "SUCCESS";
  return (
    <div className="flex items-center gap-3.5 py-3">
      <TxnIcon txn={txn} userId={userId} />
      <div className="min-w-0 flex-1">
        <p className="truncate text-[15px] font-medium text-foreground">{txnCounterparty(txn, userId)}</p>
        <p className="truncate text-xs text-muted-foreground">
          {formatDate(txn.created_at)}
          {txn.description ? ` · ${txn.description}` : ""}
        </p>
      </div>
      <div className="flex flex-col items-end gap-1">
        <Amount value={txn.amount} sign={debit ? "-" : "+"} className={failed ? "text-subtle-foreground line-through" : undefined} />
        {!done && <StatusBadge status={txn.status} />}
      </div>
    </div>
  );
}

export function ExpenseRow({ expense }: { expense: Expense }) {
  return (
    <div className="flex items-center gap-3.5 py-3">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-surface">
        <span className={cn("h-2.5 w-2.5 rounded-full", categoryTone(expense.category))} aria-hidden="true" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-[15px] font-medium text-foreground">{expense.description || categoryLabel(expense.category)}</p>
        <p className="truncate text-xs text-muted-foreground">
          {categoryLabel(expense.category)} · {formatDate(expense.created_at)}
        </p>
      </div>
      <Amount value={expense.amount} sign="-" />
    </div>
  );
}

export function EmptyState({ title, text, action, icon }: { title: string; text?: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-border-strong px-6 py-12 text-center">
      <span className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-surface text-muted-foreground">
        {icon ?? <ShoppingBag className="h-5 w-5" aria-hidden="true" />}
      </span>
      <p className="font-medium text-foreground">{title}</p>
      {text && <p className="mt-1 max-w-xs text-sm text-muted-foreground">{text}</p>}
      {action && <div className="mt-5">{action}</div>}
    </div>
  );
}

/** Totals per category, largest first. */
export function spendingByCategory(expenses: Pick<Expense, "category" | "amount">[]) {
  const totals = new Map<string, number>();
  let total = 0;
  for (const e of expenses) {
    const amt = Number(e.amount) || 0;
    total += amt;
    totals.set(e.category, (totals.get(e.category) ?? 0) + amt);
  }
  const entries = [...totals.entries()].sort((a, b) => b[1] - a[1]);
  return { total, entries };
}

export function SpendingBar({ entries, total }: { entries: [string, number][]; total: number }) {
  if (total <= 0) return <div className="h-2.5 w-full rounded-full bg-surface" />;
  return (
    <div className="flex h-2.5 w-full gap-0.5 overflow-hidden rounded-full">
      {entries.map(([cat, amt]) => (
        <span key={cat} className={cn("h-full min-w-1", categoryTone(cat))} style={{ width: `${(amt / total) * 100}%` }} />
      ))}
    </div>
  );
}
