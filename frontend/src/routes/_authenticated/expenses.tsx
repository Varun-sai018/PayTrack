import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Field, FormMessage, NativeSelect } from "@/components/field";
import { Amount, EmptyState, SpendingBar, categoryTone } from "@/components/activity";
import { PageHeader, PageSkeleton, SectionLabel } from "@/components/page-header";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { supabase } from "@/integrations/supabase/client";
import { CATEGORIES, CATEGORY_LABELS, categoryLabel, formatDate, formatINR, friendlyError, type Category } from "@/lib/paytrack";
import { expensesQuery, type Expense } from "@/lib/queries";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/_authenticated/expenses")({
  head: () => ({
    meta: [
      { title: "Expenses — PayTrack" },
      { name: "description", content: "Automatic spending categories and a manual expense tracker." },
      { property: "og:title", content: "Expenses — PayTrack" },
      { property: "og:description", content: "See where your money goes, by category." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(expensesQuery),
  pendingComponent: PageSkeleton,
  component: Expenses,
});

function Expenses() {
  const queryClient = useQueryClient();
  const { data: all } = useSuspenseQuery(expensesQuery);
  const [category, setCategory] = useState<"" | Category>("");
  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<Expense | null>(null);
  const [newCategory, setNewCategory] = useState<Category>("OTHER");
  const [deleting, setDeleting] = useState<Expense | null>(null);
  const [form, setForm] = useState<{ category: Category; amount: string; description: string }>({ category: "FOOD", amount: "", description: "" });
  const [formError, setFormError] = useState("");

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["expenses"] });
  const expenses = category ? all.filter((e) => e.category === category) : all;

  const summary = useMemo(() => {
    const breakdown: Partial<Record<Category, number>> = {};
    let total = 0;
    for (const e of all) {
      const amt = Number(e.amount);
      total += amt;
      breakdown[e.category] = (breakdown[e.category] ?? 0) + amt;
    }
    const entries = Object.entries(breakdown) as [Category, number][];
    const top = [...entries].sort((a, b) => b[1] - a[1])[0];
    return { total, entries, top: top ? categoryLabel(top[0]) : "—" };
  }, [all]);

  const create = useMutation({
    mutationFn: async (input: { category: Category; amount: number; description: string }) => {
      const { error } = await supabase.from("expenses").insert({ ...input, currency: "INR", source: "MANUAL" });
      if (error) throw error;
    },
    onSuccess: async () => {
      setShowCreate(false);
      setForm({ category: "FOOD", amount: "", description: "" });
      await refresh();
    },
    onError: (err) => setFormError(friendlyError(err, "Failed to create expense")),
  });

  const recategorise = useMutation({
    mutationFn: async ({ id, value }: { id: string; value: Category }) => {
      const { error } = await supabase.from("expenses").update({ category: value }).eq("id", id);
      if (error) throw error;
    },
    onSuccess: async () => {
      setEditing(null);
      await refresh();
    },
    onError: (err) => toast.error(friendlyError(err, "Failed to update category")),
  });

  const remove = useMutation({
    mutationFn: async (id: string) => {
      const { error } = await supabase.from("expenses").delete().eq("id", id);
      if (error) throw error;
    },
    onSuccess: async () => {
      setDeleting(null);
      await refresh();
    },
    onError: (err) => toast.error(friendlyError(err, "Failed to delete expense")),
  });

  const submitCreate = (e: FormEvent) => {
    e.preventDefault();
    setFormError("");
    const amount = parseFloat(form.amount);
    if (!amount || amount <= 0) return setFormError("Please enter a valid amount");
    if (!form.description.trim()) return setFormError("Description is required");
    create.mutate({ category: form.category, amount, description: form.description.trim() });
  };

  const sortedEntries = [...summary.entries].sort((a, b) => b[1] - a[1]);

  const openCreate = () => {
    setFormError("");
    setShowCreate(true);
  };

  return (
    <div className="animate-fade-in space-y-8">
      <PageHeader
        title="Spending"
        description="Payments are sorted into categories automatically. Log cash spends yourself."
        actions={
          <Button onClick={openCreate}>
            <Plus /> Log expense
          </Button>
        }
      />

      <Card>
        <div className="flex flex-wrap items-end justify-between gap-6">
          <div>
            <SectionLabel>Total spent</SectionLabel>
            <p className="num mt-2 font-display text-4xl font-semibold tracking-tight text-foreground">{formatINR(summary.total)}</p>
          </div>
          <div className="flex gap-8 text-sm">
            <div>
              <SectionLabel>Entries</SectionLabel>
              <p className="num mt-1.5 text-lg font-medium text-foreground">{all.length}</p>
            </div>
            <div>
              <SectionLabel>Top category</SectionLabel>
              <p className="mt-1.5 text-lg font-medium text-foreground">{summary.top}</p>
            </div>
          </div>
        </div>
        <div className="mt-6">
          <SpendingBar entries={sortedEntries} total={summary.total} />
        </div>
        {sortedEntries.length > 0 && (
          <div className="mt-5 flex flex-wrap gap-2" role="group" aria-label="Filter by category">
            <FilterChip active={category === ""} onClick={() => setCategory("")}>
              All
            </FilterChip>
            {sortedEntries.map(([cat, amt]) => (
              <FilterChip key={cat} active={category === cat} onClick={() => setCategory(category === cat ? "" : cat)}>
                <span className={cn("h-2 w-2 rounded-full", categoryTone(cat))} aria-hidden="true" />
                {categoryLabel(cat)}
                <span className="num text-muted-foreground group-aria-pressed:text-ink-muted">{formatINR(amt)}</span>
              </FilterChip>
            ))}
          </div>
        )}
      </Card>

      <Card className="p-0">
        <CardHeader className="mb-0 px-6 pb-4 pt-6">
          <CardTitle>{category ? categoryLabel(category) : "All expenses"}</CardTitle>
          <Button variant="ghost" size="sm" onClick={() => void refresh()}>
            <RefreshCw /> Refresh
          </Button>
        </CardHeader>
        {expenses.length === 0 ? (
          <div className="px-6 pb-6">
            <EmptyState
              title="No expenses here"
              text="Payments you make are added automatically, or log one yourself."
              action={<Button size="sm" onClick={openCreate}>Log expense</Button>}
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-y border-border bg-surface/60 text-xs text-muted-foreground">
                  <th className="px-6 py-2.5 font-medium">Details</th>
                  <th className="hidden px-4 py-2.5 font-medium sm:table-cell">Category</th>
                  <th className="hidden px-4 py-2.5 font-medium md:table-cell">Date</th>
                  <th className="px-4 py-2.5 text-right font-medium">Amount</th>
                  <th className="px-6 py-2.5 text-right font-medium">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {expenses.map((exp) => (
                  <tr key={exp.id} className="transition-colors hover:bg-surface/50">
                    <td className="px-6 py-3.5">
                      <div className="flex items-center gap-3">
                        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-surface">
                          <span className={cn("h-2.5 w-2.5 rounded-full", categoryTone(exp.category))} aria-hidden="true" />
                        </span>
                        <div className="min-w-0">
                          <p className="truncate font-medium text-foreground">{exp.description || "Expense"}</p>
                          <p className="truncate text-xs text-muted-foreground">
                            {exp.source === "MANUAL" ? "Logged by you" : "From a payment"}
                            {exp.txn_ref ? <span className="font-mono"> · {exp.txn_ref}</span> : null}
                            <span className="sm:hidden"> · {categoryLabel(exp.category)}</span>
                          </p>
                        </div>
                      </div>
                    </td>
                    <td className="hidden px-4 py-3.5 sm:table-cell">
                      {exp.txn_ref ? (
                        <button
                          onClick={() => {
                            setEditing(exp);
                            setNewCategory(exp.category);
                          }}
                          title="Change category"
                          aria-label={`Change category (now ${categoryLabel(exp.category)})`}
                          className="inline-flex items-center gap-1.5 rounded-full bg-surface px-2.5 py-1 text-xs font-medium text-foreground transition-colors hover:bg-border"
                        >
                          {categoryLabel(exp.category)}
                          <Pencil className="h-3 w-3 text-muted-foreground" aria-hidden="true" />
                        </button>
                      ) : (
                        <Badge>{categoryLabel(exp.category)}</Badge>
                      )}
                    </td>
                    <td className="hidden whitespace-nowrap px-4 py-3.5 text-muted-foreground md:table-cell">{formatDate(exp.created_at)}</td>
                    <td className="px-4 py-3.5 text-right">
                      <Amount value={exp.amount} sign="-" />
                    </td>
                    <td className="px-6 py-3.5 text-right">
                      {exp.source === "MANUAL" ? (
                        <button
                          onClick={() => setDeleting(exp)}
                          aria-label="Delete expense"
                          className="rounded-full p-2 text-subtle-foreground transition-colors hover:bg-destructive/10 hover:text-destructive"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      ) : (
                        <span className="text-xs text-subtle-foreground">Auto</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Dialog open={showCreate} onOpenChange={setShowCreate}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Log an expense</DialogTitle>
          </DialogHeader>
          <form onSubmit={submitCreate} className="space-y-4">
            <div>
              <label htmlFor="expense-category" className="mb-1.5 block text-[13px] font-medium text-muted-foreground">Category</label>
              <NativeSelect id="expense-category" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value as Category })}>
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>{CATEGORY_LABELS[c]}</option>
                ))}
              </NativeSelect>
            </div>
            <Field label="Amount (₹)" type="number" step="0.01" min="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} placeholder="0.00" required />
            <Field label="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="e.g. Groceries at local market" required />
            {formError && <FormMessage tone="error">{formError}</FormMessage>}
            <div className="flex gap-3 pt-2">
              <Button type="button" variant="ghost" className="flex-1" onClick={() => setShowCreate(false)} disabled={create.isPending}>Cancel</Button>
              <Button type="submit" className="flex-1" loading={create.isPending}>Save Expense</Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>Change category</DialogTitle>
            <DialogDescription>
              Update category for transaction <span className="font-mono">{editing?.txn_ref}</span>
            </DialogDescription>
          </DialogHeader>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (editing) recategorise.mutate({ id: editing.id, value: newCategory });
            }}
            className="space-y-4"
          >
            <NativeSelect aria-label="New category" value={newCategory} onChange={(e) => setNewCategory(e.target.value as Category)}>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>{CATEGORY_LABELS[c]}</option>
              ))}
            </NativeSelect>
            <div className="flex gap-2">
              <Button type="button" variant="ghost" className="flex-1" onClick={() => setEditing(null)} disabled={recategorise.isPending}>Cancel</Button>
              <Button type="submit" className="flex-1" loading={recategorise.isPending}>Update</Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this expense?</AlertDialogTitle>
            <AlertDialogDescription>
              "{deleting?.description}" ({formatINR(deleting?.amount)}) will be removed. Payment expenses are tracked automatically and can't be deleted.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              onClick={(e) => {
                e.preventDefault();
                if (deleting) remove.mutate(deleting.id);
              }}
            >
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

function FilterChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        "group inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-medium transition-colors",
        active ? "border-ink bg-ink text-ink-foreground" : "border-border bg-card text-foreground hover:border-border-strong",
      )}
    >
      {children}
    </button>
  );
}
