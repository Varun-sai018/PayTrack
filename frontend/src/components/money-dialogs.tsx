import { useEffect, useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";

import { Field, FormMessage, NativeSelect } from "@/components/field";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { supabase } from "@/integrations/supabase/client";
import {
  CATEGORIES,
  CATEGORY_LABELS,
  OPERATORS,
  formatINR,
  friendlyError,
  validateRecharge,
  validateTopup,
  validateTransfer,
  type Category,
  type Operator,
} from "@/lib/paytrack";
import { MONEY_QUERY_KEYS } from "@/lib/queries";
import { cn } from "@/lib/utils";

type DialogProps = { open: boolean; onOpenChange: (open: boolean) => void };

function useInvalidateMoney() {
  const queryClient = useQueryClient();
  return () => Promise.all(MONEY_QUERY_KEYS.map((queryKey) => queryClient.invalidateQueries({ queryKey: [...queryKey] })));
}

function useCloseAfterSuccess(success: string, onOpenChange: (open: boolean) => void, delay: number) {
  useEffect(() => {
    if (!success) return;
    const t = window.setTimeout(() => onOpenChange(false), delay);
    return () => window.clearTimeout(t);
  }, [success, onOpenChange, delay]);
}

const presetClass = "num flex-1 rounded-full border border-border bg-card px-2 py-2 text-xs font-medium text-foreground transition-colors hover:border-foreground";

export function TopupDialog({ open, onOpenChange }: DialogProps) {
  const invalidate = useInvalidateMoney();
  const [amount, setAmount] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  useCloseAfterSuccess(success, onOpenChange, 1500);

  useEffect(() => {
    if (open) {
      setAmount("");
      setError("");
      setSuccess("");
    }
  }, [open]);

  const mutation = useMutation({
    mutationFn: async (value: number) => {
      const { data, error: rpcError } = await supabase.rpc("wallet_topup", { p_amount: value });
      if (rpcError) throw rpcError;
      return data;
    },
    onSuccess: async (_data, value) => {
      setSuccess(`Successfully added ${formatINR(value)} to your wallet!`);
      setAmount("");
      await invalidate();
    },
    onError: (err) => setError(friendlyError(err, "Failed to complete top-up")),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    const value = parseFloat(amount);
    const problem = validateTopup(value);
    if (problem) return setError(problem);
    mutation.mutate(value);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Add money</DialogTitle>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4">
          <Field label="Amount (₹)" type="number" step="0.01" min="1.00" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="500.00" required autoFocus />
          <div className="flex gap-2">
            {[100, 500, 1000, 2000].map((v) => (
              <button key={v} type="button" onClick={() => setAmount(String(v))} className={presetClass}>
                ₹{v}
              </button>
            ))}
          </div>
          <p className="rounded-xl bg-surface px-3.5 py-2.5 text-xs text-muted-foreground">Test mode — no real payment is taken. The amount is added straight to your wallet.</p>
          {success && <FormMessage tone="success">{success}</FormMessage>}
          {error && <FormMessage tone="error">{error}</FormMessage>}
          <div className="flex gap-3 pt-2">
            <Button type="button" variant="ghost" className="flex-1" onClick={() => onOpenChange(false)} disabled={mutation.isPending}>
              Cancel
            </Button>
            <Button type="submit" className="flex-1" loading={mutation.isPending}>
              Add money
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function TransferDialog({ open, onOpenChange }: DialogProps) {
  const invalidate = useInvalidateMoney();
  const [form, setForm] = useState({ receiverUpiId: "", amount: "", note: "", category: "OTHER" as Category });
  const [preview, setPreview] = useState<string | null>(null);
  const [lookupLoading, setLookupLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  useCloseAfterSuccess(success, onOpenChange, 1800);

  useEffect(() => {
    if (open) {
      setForm({ receiverUpiId: "", amount: "", note: "", category: "OTHER" });
      setPreview(null);
      setError("");
      setSuccess("");
      setIdempotencyKey(crypto.randomUUID());
    }
  }, [open]);

  const lookup = async () => {
    const upi = form.receiverUpiId.trim();
    if (!upi || !upi.includes("@paytrack")) {
      setPreview(null);
      return;
    }
    setLookupLoading(true);
    const { data, error: rpcError } = await supabase.rpc("lookup_upi", { p_upi: upi });
    setLookupLoading(false);
    const match = data?.[0];
    if (rpcError || !match) {
      setPreview(null);
      setError("User not found with this UPI ID");
    } else {
      setPreview(match.masked_name);
      setError("");
    }
  };

  const mutation = useMutation({
    mutationFn: async (amount: number) => {
      const { data, error: rpcError } = await supabase.rpc("transfer_money", {
        p_receiver_upi: form.receiverUpiId.trim(),
        p_amount: amount,
        p_note: form.note.trim(),
        p_category: form.category,
        p_idempotency_key: idempotencyKey,
      });
      if (rpcError) throw rpcError;
      return data;
    },
    onSuccess: async (data, amount) => {
      setSuccess(`Payment of ${formatINR(amount)} sent successfully! (Ref: ${data.txn_ref})`);
      setForm({ receiverUpiId: "", amount: "", note: "", category: "OTHER" });
      setPreview(null);
      setIdempotencyKey(crypto.randomUUID());
      await invalidate();
    },
    onError: (err) => setError(friendlyError(err, "Transfer failed. Check your wallet balance.")),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    const amount = parseFloat(form.amount);
    const problem = validateTransfer(form.receiverUpiId, amount);
    if (problem) return setError(problem);
    mutation.mutate(amount);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Send money</DialogTitle>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4">
          <div>
            <Field
              label="To UPI ID"
              value={form.receiverUpiId}
              onChange={(e) => setForm({ ...form, receiverUpiId: e.target.value.toLowerCase() })}
              onBlur={() => void lookup()}
              placeholder="9876543210@paytrack"
              required
              autoFocus
            />
            {lookupLoading && <p className="mt-1 text-xs text-muted-foreground">Verifying recipient...</p>}
            {preview && (
              <div className="mt-2 flex items-center justify-between rounded-xl bg-success/10 px-3.5 py-2.5 text-sm">
                <span className="text-muted-foreground">Paying</span>
                <span className="font-medium text-success">{preview} ✓</span>
              </div>
            )}
          </div>
          <Field label="Amount (₹)" type="number" step="0.01" min="1.00" max="10000.00" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} placeholder="0.00" required />
          <Field label="Note (optional)" value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} placeholder="Dinner, uber ride, rent..." />
          <div>
            <label htmlFor="transfer-category" className="mb-1.5 block text-[13px] font-medium text-muted-foreground">
              Category
            </label>
            <NativeSelect id="transfer-category" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value as Category })}>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {CATEGORY_LABELS[c]}
                </option>
              ))}
            </NativeSelect>
          </div>
          {success && <FormMessage tone="success">{success}</FormMessage>}
          {error && <FormMessage tone="error">{error}</FormMessage>}
          <div className="flex gap-3 pt-2">
            <Button type="button" variant="ghost" className="flex-1" onClick={() => onOpenChange(false)} disabled={mutation.isPending}>
              Cancel
            </Button>
            <Button type="submit" className="flex-1" loading={mutation.isPending}>
              Pay now
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function RechargeDialog({ open, onOpenChange }: DialogProps) {
  const invalidate = useInvalidateMoney();
  const [form, setForm] = useState<{ mobileNumber: string; operator: Operator; amount: string }>({ mobileNumber: "", operator: "JIO", amount: "" });
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());
  useCloseAfterSuccess(success, onOpenChange, 1800);

  useEffect(() => {
    if (open) {
      setForm({ mobileNumber: "", operator: "JIO", amount: "" });
      setError("");
      setSuccess("");
      setIdempotencyKey(crypto.randomUUID());
    }
  }, [open]);

  const mutation = useMutation({
    mutationFn: async (amount: number) => {
      const { data, error: rpcError } = await supabase.rpc("mobile_recharge", {
        p_mobile: form.mobileNumber,
        p_operator: form.operator,
        p_amount: amount,
        p_idempotency_key: idempotencyKey,
      });
      if (rpcError) throw rpcError;
      return data;
    },
    onSuccess: async (_data, amount) => {
      setSuccess(`Recharge of ${formatINR(amount)} for ${form.mobileNumber} was successful!`);
      setForm({ mobileNumber: "", operator: "JIO", amount: "" });
      setIdempotencyKey(crypto.randomUUID());
      await invalidate();
    },
    onError: (err) => setError(friendlyError(err, "Recharge failed. Please check your balance.")),
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    const amount = parseFloat(form.amount);
    const problem = validateRecharge(form.mobileNumber, amount);
    if (problem) return setError(problem);
    mutation.mutate(amount);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Mobile recharge</DialogTitle>
        </DialogHeader>
        <form onSubmit={submit} className="space-y-4">
          <Field label="Mobile number" type="tel" inputMode="numeric" value={form.mobileNumber} onChange={(e) => setForm({ ...form, mobileNumber: e.target.value })} placeholder="9876543210" required autoFocus />
          <div>
            <p className="mb-1.5 block text-[13px] font-medium text-muted-foreground">Operator</p>
            <div className="grid grid-cols-4 gap-2" role="radiogroup" aria-label="Operator">
              {OPERATORS.map((op) => (
                <button
                  key={op}
                  type="button"
                  role="radio"
                  aria-checked={form.operator === op}
                  onClick={() => setForm({ ...form, operator: op })}
                  className={cn(
                    "rounded-xl border py-2.5 text-xs font-semibold transition-all",
                    form.operator === op ? "border-ink bg-ink text-ink-foreground" : "border-border bg-card text-foreground hover:border-border-strong",
                  )}
                >
                  {op}
                </button>
              ))}
            </div>
          </div>
          <Field label="Amount (₹)" type="number" step="1" min="10" max="2000" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} placeholder="299" required />
          <div className="flex gap-2">
            {[199, 299, 499, 719].map((v) => (
              <button key={v} type="button" onClick={() => setForm({ ...form, amount: String(v) })} className={presetClass}>
                ₹{v}
              </button>
            ))}
          </div>
          {success && <FormMessage tone="success">{success}</FormMessage>}
          {error && <FormMessage tone="error">{error}</FormMessage>}
          <div className="flex gap-3 pt-2">
            <Button type="button" variant="ghost" className="flex-1" onClick={() => onOpenChange(false)} disabled={mutation.isPending}>
              Cancel
            </Button>
            <Button type="submit" className="flex-1" loading={mutation.isPending}>
              Recharge
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  );
}
