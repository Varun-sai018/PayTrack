import type { ReactNode } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { useQueryClient } from "@tanstack/react-query";
import {
  Activity,
  ArrowLeftRight,
  ExternalLink,
  LogOut,
  Server,
  ShieldAlert,
  ShieldCheck,
  Users,
} from "lucide-react";

import { BrandLogo } from "@/components/brand-logo";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { api, getUser } from "@/lib/api";
import { cn } from "@/lib/utils";

export type AdminTab = "overview" | "kyc" | "users" | "transactions" | "health";

interface AdminShellProps {
  currentTab: AdminTab;
  onTabChange: (tab: AdminTab) => void;
  pendingKycCount?: number;
  userCount?: number;
  children: ReactNode;
}

const ADMIN_NAV = [
  { id: "overview" as AdminTab, label: "System Overview", Icon: Activity },
  { id: "kyc" as AdminTab, label: "KYC Queue", Icon: ShieldAlert },
  { id: "users" as AdminTab, label: "User Governance", Icon: Users },
  { id: "transactions" as AdminTab, label: "All Transactions", Icon: ArrowLeftRight },
  { id: "health" as AdminTab, label: "Services Health", Icon: Server },
];

export function AdminShell({
  currentTab,
  onTabChange,
  pendingKycCount = 0,
  userCount = 0,
  children,
}: AdminShellProps) {
  const user = getUser();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const handleSignOut = async () => {
    await queryClient.cancelQueries();
    queryClient.clear();
    await api.logout();
    await navigate({ to: "/login", replace: true });
  };

  return (
    <div className="min-h-screen bg-background">
      {/* Desktop Admin Sidebar */}
      <aside className="fixed inset-y-0 left-0 z-40 hidden w-64 flex-col border-r border-border bg-sidebar px-4 py-6 md:flex">
        {/* Brand Header */}
        <div className="px-2">
          <div className="flex items-center justify-between">
            <Link to="/admin" aria-label="PayTrack Admin home">
              <BrandLogo />
            </Link>
            <Badge variant="accent" className="text-[10px] font-bold tracking-wider">
              ADMIN
            </Badge>
          </div>
          <p className="mt-1 text-xs text-muted-foreground">Operations & Compliance Portal</p>
        </div>

        {/* Live System Indicator */}
        <div className="mt-6 rounded-xl border border-border/60 bg-surface/80 p-3">
          <div className="flex items-center gap-2">
            <span className="relative flex h-2.5 w-2.5">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-success opacity-75" />
              <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-success" />
            </span>
            <span className="text-xs font-semibold text-foreground">API Gateway Live</span>
          </div>
          <p className="mt-1 font-mono text-[11px] text-muted-foreground">:8089 • Eureka Connected</p>
        </div>

        {/* Admin Navigation */}
        <nav className="mt-6 flex flex-col gap-1.5" aria-label="Admin navigation">
          {ADMIN_NAV.map(({ id, label, Icon }) => {
            const active = currentTab === id;
            return (
              <button
                key={id}
                type="button"
                onClick={() => onTabChange(id)}
                className={cn(
                  "flex w-full items-center justify-between rounded-xl px-3 py-2.5 text-left text-sm font-medium transition-all cursor-pointer",
                  active
                    ? "bg-card text-foreground shadow-card font-semibold"
                    : "text-muted-foreground hover:bg-card/60 hover:text-foreground",
                )}
              >
                <div className="flex items-center gap-3">
                  <Icon
                    className={cn(
                      "h-4 w-4",
                      active ? "text-highlight-foreground" : "text-subtle-foreground",
                    )}
                  />
                  <span>{label}</span>
                </div>
                {id === "kyc" && pendingKycCount > 0 && (
                  <span className="rounded-full bg-warning/20 px-2 py-0.5 text-xs font-bold text-warning">
                    {pendingKycCount}
                  </span>
                )}
                {id === "users" && userCount > 0 && (
                  <span className="text-xs text-muted-foreground">{userCount}</span>
                )}
              </button>
            );
          })}
        </nav>

        {/* Footer: Switch to Customer App & Admin User Info */}
        <div className="mt-auto space-y-4 pt-4 border-t border-border/50">
          <Link
            to="/dashboard"
            className="flex items-center justify-between rounded-xl border border-border/60 bg-surface/50 px-3 py-2 text-xs font-medium text-muted-foreground transition-colors hover:bg-card hover:text-foreground"
          >
            <span className="flex items-center gap-2">
              <ExternalLink className="h-3.5 w-3.5" /> Customer Dashboard
            </span>
            <span className="text-[10px] uppercase tracking-wider text-muted-foreground">Switch</span>
          </Link>

          <div className="flex items-center justify-between rounded-xl bg-card p-2.5 shadow-sm">
            <div className="flex items-center gap-2.5 min-w-0">
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-highlight text-xs font-bold text-highlight-foreground">
                AD
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-xs font-semibold text-foreground">
                  {user?.fullName || "Administrator"}
                </p>
                <p className="truncate font-mono text-[11px] text-muted-foreground">
                  {user?.email || "admin@paytrack.com"}
                </p>
              </div>
            </div>
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7 text-destructive hover:bg-destructive/10"
              title="Sign Out"
              onClick={handleSignOut}
            >
              <LogOut className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>
      </aside>

      {/* Mobile Top Header */}
      <header className="sticky top-0 z-40 flex h-header items-center justify-between border-b border-border bg-background/90 px-4 backdrop-blur-md md:hidden">
        <div className="flex items-center gap-2">
          <BrandLogo />
          <Badge variant="accent" className="text-[10px] font-bold">
            ADMIN
          </Badge>
        </div>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              className="flex h-8 w-8 items-center justify-center rounded-full bg-highlight text-xs font-bold text-highlight-foreground"
            >
              AD
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel className="font-normal">
              <p className="text-sm font-medium">{user?.fullName || "Administrator"}</p>
              <p className="text-xs text-muted-foreground">{user?.email}</p>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <Link to="/dashboard" className="cursor-pointer">
                <ExternalLink className="h-4 w-4" /> Customer Dashboard
              </Link>
            </DropdownMenuItem>
            <DropdownMenuItem
              onSelect={() => void handleSignOut()}
              className="cursor-pointer text-destructive focus:text-destructive"
            >
              <LogOut className="h-4 w-4" /> Sign out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </header>

      {/* Main Content Area */}
      <main className="pb-24 md:pb-8 md:pl-64">
        <div className="px-4 py-6 sm:px-6 lg:px-8 max-w-7xl mx-auto">{children}</div>
      </main>

      {/* Mobile Bottom Tabs */}
      <nav
        className="fixed inset-x-3 bottom-3 z-40 grid grid-cols-5 rounded-2xl border border-border bg-card/95 p-1.5 shadow-lift backdrop-blur-md md:hidden"
        aria-label="Admin mobile navigation"
      >
        {ADMIN_NAV.map(({ id, label, Icon }) => {
          const active = currentTab === id;
          return (
            <button
              key={id}
              type="button"
              onClick={() => onTabChange(id)}
              className={cn(
                "flex flex-col items-center gap-0.5 rounded-xl py-1.5 text-[10px] font-medium transition-colors relative",
                active ? "bg-ink text-ink-foreground font-semibold" : "text-muted-foreground",
              )}
            >
              <Icon className="h-4 w-4" />
              <span className="truncate max-w-[56px]">{label.split(" ")[0]}</span>
              {id === "kyc" && pendingKycCount > 0 && (
                <span className="absolute top-1 right-2 h-2 w-2 rounded-full bg-warning" />
              )}
            </button>
          );
        })}
      </nav>
    </div>
  );
}
