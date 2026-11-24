import type { ReactNode } from "react";
import { Copy } from "lucide-react";
import { toast } from "sonner";

import { formatINR } from "@/lib/paytrack";
import { cn } from "@/lib/utils";

export function BalanceCard({
  label = "Total balance",
  balance,
  upi,
  children,
  className,
}: {
  label?: string;
  balance: number | string | null | undefined;
  upi?: string | null;
  children?: ReactNode;
  className?: string;
}) {
  const formatted = formatINR(balance ?? 0);
  const [whole, paise] = formatted.split(".");

  const copy = async () => {
    if (!upi) return;
    try {
      await navigator.clipboard.writeText(upi);
      toast.success("UPI ID copied");
    } catch {
      toast.error("Couldn't copy — select it manually");
    }
  };

  return (
    <section className={cn("relative overflow-hidden rounded-3xl bg-ink p-6 text-ink-foreground sm:p-8", className)}>
      <div aria-hidden="true" className="pointer-events-none absolute -right-16 -top-24 h-64 w-64 rounded-full border-[28px] border-highlight/[0.07]" />
      <div className="relative flex flex-wrap items-start justify-between gap-3">
        <p className="text-sm text-ink-muted">{label}</p>
        {upi && (
          <button
            type="button"
            onClick={() => void copy()}
            className="flex items-center gap-2 rounded-full bg-ink-line/70 px-3 py-1.5 font-mono text-xs text-ink-foreground transition-colors hover:bg-ink-line"
            aria-label={`Copy UPI ID ${upi}`}
          >
            {upi}
            <Copy className="h-3.5 w-3.5 text-ink-muted" aria-hidden="true" />
          </button>
        )}
      </div>
      <p className="num relative mt-3 font-display text-5xl font-semibold tracking-tight sm:text-6xl">
        {whole}
        <span className="text-ink-muted">.{paise}</span>
      </p>
      {children && <div className="relative mt-8">{children}</div>}
    </section>
  );
}

export function BalanceAction({
  icon,
  label,
  onClick,
  primary,
}: {
  icon: ReactNode;
  label: string;
  onClick: () => void;
  primary?: boolean;
}) {
  return (
    <button type="button" onClick={onClick} className="group flex flex-col items-center gap-2 text-xs font-medium text-ink-foreground">
      <span
        className={cn(
          "flex h-12 w-12 items-center justify-center rounded-full transition-transform group-hover:-translate-y-0.5 [&_svg]:h-5 [&_svg]:w-5",
          primary ? "bg-highlight text-highlight-foreground" : "bg-ink-line text-ink-foreground",
        )}
      >
        {icon}
      </span>
      {label}
    </button>
  );
}
