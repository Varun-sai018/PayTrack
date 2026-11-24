import type { ReactNode } from "react";
import { Link } from "@tanstack/react-router";
import { ArrowDownLeft, ArrowUpRight, Smartphone } from "lucide-react";

import { BrandLogo, BrandMark } from "@/components/brand-logo";

export function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1fr_minmax(0,560px)] xl:grid-cols-[1fr_640px]">
      <div className="flex flex-col px-6 py-8 sm:px-10">
        <Link to="/login" aria-label="PayTrack home" className="self-start">
          <BrandLogo />
        </Link>
        <div className="mx-auto flex w-full max-w-[400px] flex-1 flex-col justify-center py-12">
          <div className="animate-slide-up">
            <h1 className="font-display text-[34px] font-semibold tracking-tight text-foreground">{title}</h1>
            <p className="mt-2 text-[15px] text-muted-foreground">{subtitle}</p>
            <div className="mt-8">{children}</div>
            {footer && <div className="mt-8 text-sm text-muted-foreground">{footer}</div>}
          </div>
        </div>
        <p className="text-xs text-subtle-foreground">© {new Date().getFullYear()} PayTrack · UPI wallet for India</p>
      </div>

      <aside className="relative m-3 hidden overflow-hidden rounded-[32px] bg-ink p-12 text-ink-foreground lg:flex lg:flex-col">
        <p className="max-w-sm font-display text-[44px] font-semibold leading-[1.02] tracking-tight">
          Every rupee, <span className="text-highlight">accounted for.</span>
        </p>
        <p className="mt-5 max-w-sm text-[15px] leading-relaxed text-ink-muted">
          Send money over UPI, recharge your phone and see exactly where your money goes — all from one wallet.
        </p>

        <div className="relative mt-auto">
          <div className="w-full max-w-[360px] rounded-3xl bg-highlight p-6 text-highlight-foreground shadow-panel">
            <div className="flex items-center justify-between">
              <BrandMark className="h-7 w-7" />
              <span className="font-mono text-xs opacity-70">9876543210@paytrack</span>
            </div>
            <p className="mt-10 text-xs font-medium opacity-70">Available balance</p>
            <p className="num font-display text-4xl font-semibold tracking-tight">₹24,580.00</p>
          </div>

          <div className="ml-auto -mt-6 w-full max-w-[300px] space-y-1 rounded-2xl border border-ink-line bg-ink p-2 shadow-panel">
            {[
              { Icon: ArrowDownLeft, title: "From Aarav S.", amount: "+₹1,200.00", positive: true },
              { Icon: Smartphone, title: "Jio recharge", amount: "−₹299.00", positive: false },
              { Icon: ArrowUpRight, title: "To Meera K.", amount: "−₹450.00", positive: false },
            ].map(({ Icon, title, amount, positive }) => (
              <div key={title} className="flex items-center gap-3 rounded-xl px-3 py-2.5">
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-ink-line">
                  <Icon className="h-4 w-4" aria-hidden="true" />
                </span>
                <span className="flex-1 text-sm">{title}</span>
                <span className={positive ? "num text-sm font-medium text-highlight" : "num text-sm text-ink-muted"}>{amount}</span>
              </div>
            ))}
          </div>
        </div>
      </aside>
    </div>
  );
}
