import { createFileRoute, Outlet, redirect, useRouterState } from "@tanstack/react-router";

import { AppShell } from "@/components/app-shell";
import { getToken, getUser } from "@/lib/api";

// Sessions live in the browser, so protected pages render client-side only.
export const Route = createFileRoute("/_authenticated")({
  ssr: false,
  beforeLoad: async ({ location }) => {
    const token = getToken();
    const user = getUser();
    if (!token || !user) {
      throw redirect({ to: "/login", search: { redirect: location.href } });
    }
    return { userId: user.userId };
  },
  pendingComponent: FullPageLoader,
  component: AuthenticatedLayout,
});

function AuthenticatedLayout() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });

  // Admin section uses its own dedicated Admin Layout rather than the customer AppShell
  if (pathname.startsWith("/admin")) {
    return <Outlet />;
  }

  return (
    <AppShell>
      <Outlet />
    </AppShell>
  );
}

function FullPageLoader() {
  return (
    <div className="flex min-h-screen items-center justify-center">
      <div className="flex flex-col items-center gap-4">
        <div className="h-10 w-10 animate-spin rounded-full border-[3px] border-border border-t-foreground" aria-label="Loading" />
        <p className="text-sm text-muted-foreground">Loading PayTrack...</p>
      </div>
    </div>
  );
}
