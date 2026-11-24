import { useMemo, useState } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { ArrowLeftRight, ArrowUpRight, Plus, Receipt, Smartphone } from "lucide-react";

import { EmptyState, ExpenseRow, SpendingBar, TxnRow, categoryTone, spendingByCategory } from "@/components/activity";
import { BalanceAction, BalanceCard } from "@/components/balance-card";
import { KycBanner, KycDialog } from "@/components/kyc-section";
import { RechargeDialog, TopupDialog, TransferDialog } from "@/components/money-dialogs";
import { PageHeader, PageSkeleton } from "@/components/page-header";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import { categoryLabel, formatINR, upiFromPhone } from "@/lib/paytrack";
import { expensesQuery, profileQuery, transactionsQuery, walletQuery } from "@/lib/queries";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/_authenticated/dashboard")({
  head: () => ({
    meta: [
      { title: "Home — PayTrack" },
      { name: "description", content: "Your PayTrack balance, recent payments and this month's spending at a glance." },
      { property: "og:title", content: "Home — PayTrack" },
      { property: "og:description", content: "Balance, recent payments and spending in one place." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  loader: ({ context }) =>
    Promise.all([
      context.queryClient.ensureQueryData(profileQuery),
      context.queryClient.ensureQueryData(walletQuery),
      context.queryClient.ensureQueryData(transactionsQuery(5)),
      context.queryClient.ensureQueryData(expensesQuery),
    ]),
  pendingComponent: PageSkeleton,
  component: Dashboard,
});

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
}

function Dashboard() {
  const { userId } = Route.useRouteContext();
  const navigate = useNavigate();
  const { data: profile } = useSuspenseQuery(profileQuery);
  const { data: wallet } = useSuspenseQuery(walletQuery);
  const { data: txns } = useSuspenseQuery(transactionsQuery(5));
  const { data: expenses } = useSuspenseQuery(expensesQuery);
  const [dialog, setDialog] = useState<"topup" | "transfer" | "recharge" | null>(null);
  const [kycDialogOpen, setKycDialogOpen] = useState(false);
  const close = (open: boolean) => !open && setDialog(null);

  const month = useMemo(() => {
    const now = new Date();
    const thisMonth = expenses.filter((e) => {
      const d = new Date(e.created_at);
      return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
    });
    return { ...spendingByCategory(thisMonth), name: now.toLocaleDateString("en-IN", { month: "long" }) };
  }, [expenses]);

  const firstName = profile?.full_name?.split(" ")[0] || "there";

  return (
    <div className="animate-fade-in space-y-8">
      <PageHeader
        eyebrow={greeting()}
        title={firstName}
      />

      <KycBanner
        kycStatus={profile?.kyc_status}
        onOpenDialog={() => setKycDialogOpen(true)}
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1.35fr_1fr]">
        <BalanceCard balance={wallet?.balance} upi={upiFromPhone(profile?.phone)}>
          <div className="grid max-w-sm grid-cols-4 gap-2">
            <BalanceAction primary icon={<Plus />} label="Add" onClick={() => setDialog("topup")} />
            <BalanceAction icon={<ArrowUpRight />} label="Send" onClick={() => setDialog("transfer")} />
            <BalanceAction icon={<Smartphone />} label="Recharge" onClick={() => setDialog("recharge")} />
            <BalanceAction icon={<Receipt />} label="Expense" onClick={() => void navigate({ to: "/expenses" })} />
          </div>
        </BalanceCard>

        <Card className="flex flex-col">
          <div className="flex items-center justify-between">
            <p className="text-sm text-muted-foreground">Spent in {month.name}</p>
            <Link to="/expenses" className="text-sm font-medium text-muted-foreground hover:text-foreground">
              Details
            </Link>
          </div>
          <p className="num mt-2 font-display text-4xl font-semibold tracking-tight text-foreground">{formatINR(month.total)}</p>
          <div className="mt-6">
            <SpendingBar entries={month.entries} total={month.total} />
          </div>
          {month.entries.length === 0 ? (
            <p className="mt-6 text-sm text-muted-foreground">Nothing spent yet this month.</p>
          ) : (
            <ul className="mt-6 space-y-3">
              {month.entries.slice(0, 4).map(([cat, amt]) => (
                <li key={cat} className="flex items-center gap-3 text-sm">
                  <span className={cn("h-2.5 w-2.5 rounded-full", categoryTone(cat))} aria-hidden="true" />
                  <span className="flex-1 text-foreground">{categoryLabel(cat)}</span>
                  <span className="num text-muted-foreground">{Math.round((amt / month.total) * 100)}%</span>
                  <span className="num w-24 text-right font-medium text-foreground">{formatINR(amt)}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1.35fr_1fr]">
        <Card>
          <CardHeader className="mb-2">
            <CardTitle>Recent payments</CardTitle>
            <Link to="/transactions" className="text-sm font-medium text-muted-foreground hover:text-foreground">
              See all
            </Link>
          </CardHeader>
          {txns.rows.length === 0 ? (
            <EmptyState
              icon={<ArrowLeftRight className="h-5 w-5" aria-hidden="true" />}
              title="No payments yet"
              text="Send money to any @paytrack UPI ID and it will show up here."
              action={<Button size="sm" onClick={() => setDialog("transfer")}>Send money</Button>}
            />
          ) : (
            <div className="divide-y divide-border">
              {txns.rows.map((txn) => (
                <TxnRow key={txn.id} txn={txn} userId={userId} />
              ))}
            </div>
          )}
        </Card>

        <Card>
          <CardHeader className="mb-2">
            <CardTitle>Recent spending</CardTitle>
            <Link to="/expenses" className="text-sm font-medium text-muted-foreground hover:text-foreground">
              See all
            </Link>
          </CardHeader>
          {expenses.length === 0 ? (
            <EmptyState
              title="No spending logged"
              text="Payments are added automatically. You can also log cash spends."
              action={
                <Button size="sm" variant="secondary" asChild>
                  <Link to="/expenses">Log an expense</Link>
                </Button>
              }
            />
          ) : (
            <div className="divide-y divide-border">
              {expenses.slice(0, 5).map((exp) => (
                <ExpenseRow key={exp.id} expense={exp} />
              ))}
            </div>
          )}
        </Card>
      </div>

      <TopupDialog open={dialog === "topup"} onOpenChange={close} />
      <TransferDialog open={dialog === "transfer"} onOpenChange={close} />
      <RechargeDialog open={dialog === "recharge"} onOpenChange={close} />
      <KycDialog open={kycDialogOpen} onOpenChange={setKycDialogOpen} profile={profile ?? null} />
    </div>
  );
}
