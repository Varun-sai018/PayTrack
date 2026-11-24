import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { useQuery, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { ArrowDownLeft, ArrowUpRight, Plus, RefreshCw } from "lucide-react";

import { Amount, EmptyState } from "@/components/activity";
import { BalanceCard } from "@/components/balance-card";
import { TopupDialog, TransferDialog } from "@/components/money-dialogs";
import { PageHeader, PageSkeleton, SectionLabel } from "@/components/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDate, formatINR, upiFromPhone } from "@/lib/paytrack";
import { ledgerQuery, profileQuery, walletQuery } from "@/lib/queries";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/_authenticated/wallet")({
  head: () => ({
    meta: [
      { title: "Wallet — PayTrack" },
      { name: "description", content: "Your PayTrack balance, top-ups and full wallet statement." },
      { property: "og:title", content: "Wallet — PayTrack" },
      { property: "og:description", content: "Add money and review every credit and debit." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  loader: ({ context }) =>
    Promise.all([context.queryClient.ensureQueryData(walletQuery), context.queryClient.ensureQueryData(ledgerQuery)]),
  pendingComponent: PageSkeleton,
  component: WalletPage,
});

function WalletPage() {
  const queryClient = useQueryClient();
  const { data: wallet } = useSuspenseQuery(walletQuery);
  const { data: statement } = useSuspenseQuery(ledgerQuery);
  const { data: profile } = useQuery(profileQuery);
  const [dialog, setDialog] = useState<"topup" | "transfer" | null>(null);
  const close = (open: boolean) => !open && setDialog(null);
  const refresh = () =>
    Promise.all([queryClient.invalidateQueries({ queryKey: ["wallet"] }), queryClient.invalidateQueries({ queryKey: ["ledger"] })]);

  const moneyIn = statement.filter((e) => e.entry_type === "CREDIT").reduce((s, e) => s + Number(e.amount), 0);
  const moneyOut = statement.filter((e) => e.entry_type !== "CREDIT").reduce((s, e) => s + Number(e.amount), 0);

  return (
    <div className="animate-fade-in space-y-8">
      <PageHeader title="Wallet" description="Add money, send it on, and see every rupee that moved." />

      {wallet ? (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1.35fr_1fr]">
          <BalanceCard label="Available balance" balance={wallet.balance} upi={upiFromPhone(profile?.phone)}>
            <div className="flex flex-wrap items-center gap-3">
              <Button variant="highlight" onClick={() => setDialog("topup")}>
                <Plus /> Add money
              </Button>
              <Button
                className="bg-ink-line text-ink-foreground hover:bg-ink-line/80"
                onClick={() => setDialog("transfer")}
              >
                <ArrowUpRight /> Send to UPI
              </Button>
            </div>
          </BalanceCard>

          <Card className="grid grid-cols-2 content-between gap-6">
            <div>
              <SectionLabel>Money in</SectionLabel>
              <p className="num mt-2 font-display text-2xl font-semibold text-success">{formatINR(moneyIn)}</p>
            </div>
            <div>
              <SectionLabel>Money out</SectionLabel>
              <p className="num mt-2 font-display text-2xl font-semibold text-foreground">{formatINR(moneyOut)}</p>
            </div>
            <div className="col-span-2 flex items-center justify-between border-t border-border pt-5 text-sm">
              <span className="text-muted-foreground">
                Wallet <span className="font-mono text-foreground">{wallet.id.slice(0, 8)}</span>
              </span>
              <span className="flex gap-1.5">
                <Badge>{wallet.currency || "INR"}</Badge>
                <Badge variant={wallet.status === "ACTIVE" ? "success" : "default"}>{wallet.status || "ACTIVE"}</Badge>
              </span>
            </div>
          </Card>
        </div>
      ) : (
        <Card>
          <EmptyState
            title="No wallet found"
            text="Your wallet is normally created when you sign up."
            action={<Button onClick={() => void refresh()}>Check again</Button>}
          />
        </Card>
      )}

      <Card className="p-0">
        <CardHeader className="mb-0 px-6 pb-4 pt-6">
          <CardTitle>Statement</CardTitle>
          <Button variant="ghost" size="sm" onClick={() => void refresh()}>
            <RefreshCw /> Refresh
          </Button>
        </CardHeader>
        {statement.length === 0 ? (
          <div className="px-6 pb-6">
            <EmptyState title="Nothing here yet" text="Top-ups, payments and recharges will be listed here." />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-y border-border bg-surface/60 text-xs text-muted-foreground">
                  <th className="px-6 py-2.5 font-medium">Details</th>
                  <th className="hidden px-4 py-2.5 font-medium md:table-cell">Date</th>
                  <th className="px-4 py-2.5 text-right font-medium">Amount</th>
                  <th className="hidden px-6 py-2.5 text-right font-medium sm:table-cell">Balance</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {statement.map((entry) => {
                  const credit = entry.entry_type === "CREDIT";
                  return (
                    <tr key={entry.id} className="transition-colors hover:bg-surface/50">
                      <td className="px-6 py-3.5">
                        <div className="flex items-center gap-3">
                          <span
                            className={cn(
                              "flex h-9 w-9 shrink-0 items-center justify-center rounded-full",
                              credit ? "bg-success/12 text-success" : "bg-surface text-foreground",
                            )}
                          >
                            {credit ? <ArrowDownLeft className="h-4 w-4" /> : <ArrowUpRight className="h-4 w-4" />}
                          </span>
                          <div className="min-w-0">
                            <p className="truncate font-medium text-foreground">{entry.description || entry.reference_id || "Wallet entry"}</p>
                            <p className="text-xs text-muted-foreground md:hidden">{formatDate(entry.created_at, true)}</p>
                          </div>
                        </div>
                      </td>
                      <td className="hidden whitespace-nowrap px-4 py-3.5 text-muted-foreground md:table-cell">
                        {formatDate(entry.created_at, true)}
                      </td>
                      <td className="px-4 py-3.5 text-right">
                        <Amount value={entry.amount} sign={credit ? "+" : "-"} />
                      </td>
                      <td className="num hidden px-6 py-3.5 text-right text-muted-foreground sm:table-cell">{formatINR(entry.balance_after)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <TopupDialog open={dialog === "topup"} onOpenChange={close} />
      <TransferDialog open={dialog === "transfer"} onOpenChange={close} />
    </div>
  );
}
