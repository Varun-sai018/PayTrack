import type { ReactNode } from "react";
import { Link, useNavigate, useRouterState } from "@tanstack/react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeftRight, ChevronsUpDown, Home, LogOut, PieChart, Settings, Wallet } from "lucide-react";

import { BrandLogo } from "@/components/brand-logo";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { api } from "@/lib/api";
import { formatINR } from "@/lib/paytrack";
import { profileQuery, walletQuery } from "@/lib/queries";
import { cn } from "@/lib/utils";

const NAV = [
  { to: "/dashboard", label: "Home", Icon: Home },
  { to: "/wallet", label: "Wallet", Icon: Wallet },
  { to: "/transactions", label: "Payments", Icon: ArrowLeftRight },
  { to: "/expenses", label: "Spending", Icon: PieChart },
  { to: "/settings", label: "Settings", Icon: Settings },
] as const;

function useSignOut() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.cancelQueries();
    queryClient.clear();
    await api.logout();
    await navigate({ to: "/login", replace: true });
  };
}

function initialsOf(name?: string | null, email?: string | null) {
  const source = name?.trim() || email || "U";
  const parts = source.split(/\s+/).filter(Boolean);
  const letters = parts.length > 1 ? `${parts[0]?.[0] ?? ""}${parts[1]?.[0] ?? ""}` : source.slice(0, 1);
  return letters.toUpperCase();
}

function Avatar({ name, email, className }: { name?: string | null | undefined; email?: string | null | undefined; className?: string }) {
  return (
    <span
      className={cn(
        "flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-highlight text-xs font-semibold text-highlight-foreground",
        className,
      )}
    >
      {initialsOf(name, email)}
    </span>
  );
}

function UserMenu({ trigger, align = "end" }: { trigger: ReactNode; align?: "start" | "end" }) {
  const { data: profile } = useQuery(profileQuery);
  const signOut = useSignOut();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>{trigger}</DropdownMenuTrigger>
      <DropdownMenuContent align={align} side="top" className="w-60">
        <DropdownMenuLabel className="font-normal">
          <p className="text-sm font-medium text-foreground">{profile?.full_name || "Your account"}</p>
          <p className="truncate text-xs text-muted-foreground">{profile?.email}</p>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link to="/settings" className="cursor-pointer">
            <Settings className="h-4 w-4" /> Settings
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => void signOut()} className="cursor-pointer text-destructive focus:text-destructive">
          <LogOut className="h-4 w-4" /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const { data: profile } = useQuery(profileQuery);
  const { data: wallet } = useQuery(walletQuery);

  return (
    <div className="min-h-screen">
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-sidebar flex-col border-r border-border bg-sidebar px-4 py-6 md:flex">
        <Link to="/dashboard" aria-label="PayTrack home" className="px-2">
          <BrandLogo />
        </Link>

        <Link
          to="/wallet"
          className="mt-8 block rounded-2xl bg-ink px-4 py-4 text-ink-foreground transition-transform hover:-translate-y-0.5"
        >
          <p className="text-xs text-ink-muted">Balance</p>
          <p className="num mt-1 font-display text-xl font-semibold">{wallet ? formatINR(wallet.balance) : "₹—"}</p>
        </Link>

        <nav className="mt-6 flex flex-col gap-1" aria-label="Main navigation">
          {NAV.map(({ to, label, Icon }) => {
            const active = pathname === to;
            return (
              <Link
                key={to}
                to={to}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition-colors",
                  active ? "bg-card text-foreground shadow-card" : "text-muted-foreground hover:bg-card/60 hover:text-foreground",
                )}
              >
                <Icon className={cn("h-[18px] w-[18px]", active ? "text-foreground" : "text-subtle-foreground")} aria-hidden="true" />
                {label}
              </Link>
            );
          })}
        </nav>

        <div className="mt-auto">
          <UserMenu
            align="start"
            trigger={
              <button
                type="button"
                className="flex w-full items-center gap-3 rounded-xl px-2 py-2 text-left transition-colors hover:bg-card"
                aria-label="Account menu"
              >
                <Avatar name={profile?.full_name} email={profile?.email} />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-medium text-foreground">{profile?.full_name || "Your account"}</span>
                  <span className="block truncate text-xs text-muted-foreground">{profile?.email}</span>
                </span>
                <ChevronsUpDown className="h-4 w-4 text-subtle-foreground" aria-hidden="true" />
              </button>
            }
          />
        </div>
      </aside>

      {/* Mobile top bar */}
      <header className="sticky top-0 z-40 flex h-header items-center justify-between border-b border-border bg-background/85 px-5 backdrop-blur-md md:hidden">
        <Link to="/dashboard" aria-label="PayTrack home">
          <BrandLogo />
        </Link>
        <UserMenu
          trigger={
            <button type="button" aria-label="Account menu" className="rounded-full">
              <Avatar name={profile?.full_name} email={profile?.email} />
            </button>
          }
        />
      </header>

      <main className="pb-28 md:pb-0 md:pl-sidebar">
        <div className="container-app py-6 md:py-10">{children}</div>
      </main>

      {/* Mobile bottom tabs */}
      <nav
        className="fixed inset-x-3 bottom-3 z-40 grid grid-cols-5 rounded-2xl border border-border bg-card/95 p-1.5 shadow-lift backdrop-blur-md md:hidden"
        aria-label="Main navigation"
      >
        {NAV.map(({ to, label, Icon }) => {
          const active = pathname === to;
          return (
            <Link
              key={to}
              to={to}
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex flex-col items-center gap-0.5 rounded-xl py-2 text-[11px] font-medium transition-colors",
                active ? "bg-ink text-ink-foreground" : "text-muted-foreground",
              )}
            >
              <Icon className="h-[18px] w-[18px]" aria-hidden="true" />
              {label}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
