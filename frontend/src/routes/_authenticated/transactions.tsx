import { useState } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { ArrowLeftRight, ArrowUpRight, RefreshCw, Smartphone } from "lucide-react";

import { Amount, EmptyState, TxnIcon, TxnRow } from "@/components/activity";
import { RechargeDialog, TransferDialog } from "@/components/money-dialogs";
import { PageHeader, PageSkeleton } from "@/components/page-header";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import { categoryLabel, formatDate, isDebitFor, txnCounterparty } from "@/lib/paytrack";
import { transactionsQuery } from "@/lib/queries";

const PAGE_SIZE = 20;

export const Route = createFileRoute("/_authenticated/transactions")({
  head: () => ({
    meta: [
      { title: "Payments — PayTrack" },
      { name: "description", content: "Send money over UPI, recharge your mobile and review past payments." },
      { property: "og:title", content: "Payments — PayTrack" },
      { property: "og:description", content: "UPI transfers, mobile recharges and your full history." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(transactionsQuery(PAGE_SIZE)),
  pendingComponent: PageSkeleton,
  component: Transactions,
});

function Transactions() {
  const { userId } = Route.useRouteContext();
  const queryClient = useQueryClient();
  const { data } = useSuspenseQuery(transactionsQuery(PAGE_SIZE));
  const [dialog, setDialog] = useState<"transfer" | "recharge" | null>(null);
  const close = (open: boolean) => !open && setDialog(null);

  return (
    <div className="animate-fade-in space-y-8">
      <PageHeader
        title="Payments"
        description="Send money over UPI, recharge a phone, and look back at every payment."
        actions={
          <>
            <Button variant="secondary" onClick={() => setDialog("recharge")}>
              <Smartphone /> Recharge
            </Button>
            <Button onClick={() => setDialog("transfer")}>
              <ArrowUpRight /> Send money
            </Button>
          </>
        }
      />

      <Card className="p-0">
        <CardHeader className="mb-0 px-6 pb-4 pt-6">
          <CardTitle>
            History <span className="num ml-1 text-base font-normal text-muted-foreground">{data.total}</span>
          </CardTitle>
          <Button variant="ghost" size="sm" onClick={() => void queryClient.invalidateQueries({ queryKey: ["transactions"] })}>
            <RefreshCw /> Refresh
          </Button>
        </CardHeader>
        {data.rows.length === 0 ? (
          <div className="px-6 pb-6">
            <EmptyState
              icon={<ArrowLeftRight className="h-5 w-5" aria-hidden="true" />}
              title="No payments yet"
              text="Your transfers and recharges will appear here."
              action={<Button size="sm" onClick={() => setDialog("transfer")}>Make your first transfer</Button>}
            />
          </div>
        ) : (
          <>
            <div className="divide-y divide-border px-6 pb-2 md:hidden">
              {data.rows.map((txn) => (
                <TxnRow key={txn.id} txn={txn} userId={userId} />
              ))}
            </div>
            <table className="hidden w-full text-left text-sm md:table">
              <thead>
                <tr className="border-y border-border bg-surface/60 text-xs text-muted-foreground">
                  <th className="px-6 py-2.5 font-medium">Payment</th>
                  <th className="px-4 py-2.5 font-medium">Reference</th>
                  <th className="px-4 py-2.5 font-medium">Date</th>
                  <th className="px-4 py-2.5 font-medium">Status</th>
                  <th className="px-6 py-2.5 text-right font-medium">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {data.rows.map((txn) => {
                  const debit = isDebitFor(txn, userId);
                  const note = txn.description || (txn.requested_category ? categoryLabel(txn.requested_category) : "");
                  return (
                    <tr key={txn.id} className="transition-colors hover:bg-surface/50">
                      <td className="px-6 py-3.5">
                        <div className="flex items-center gap-3">
                          <TxnIcon txn={txn} userId={userId} />
                          <div className="min-w-0">
                            <p className="truncate font-medium text-foreground">{txnCounterparty(txn, userId)}</p>
                            <p className="max-w-xs truncate text-xs text-muted-foreground">
                              {txn.type === "RECHARGE" ? "Mobile recharge" : debit ? "Sent" : "Received"}
                              {note ? ` · ${note}` : ""}
                            </p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3.5 font-mono text-xs text-muted-foreground">{txn.txn_ref}</td>
                      <td className="whitespace-nowrap px-4 py-3.5 text-muted-foreground">{formatDate(txn.created_at, true)}</td>
                      <td className="px-4 py-3.5"><StatusBadge status={txn.status} /></td>
                      <td className="px-6 py-3.5 text-right">
                        <Amount
                          value={txn.amount}
                          sign={debit ? "-" : "+"}
                          className={txn.status === "FAILED" ? "text-subtle-foreground line-through" : undefined}
                        />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </>
        )}
      </Card>

      <TransferDialog open={dialog === "transfer"} onOpenChange={close} />
      <RechargeDialog open={dialog === "recharge"} onOpenChange={close} />
    </div>
  );
}
