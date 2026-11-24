import { useMemo, useState } from "react";
import { createFileRoute, redirect } from "@tanstack/react-router";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import {
  Activity,
  AlertCircle,
  ArrowLeftRight,
  Check,
  CheckCircle2,
  Clock,
  Filter,
  RefreshCw,
  Search,
  Server,
  ShieldAlert,
  ShieldCheck,
  UserCheck,
  UserX,
  Users,
  XCircle,
  Zap,
} from "lucide-react";
import { toast } from "sonner";

import { AdminShell, type AdminTab } from "@/components/admin-shell";
import { PageHeader } from "@/components/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { api, getUser, type AdminUser, type KycItem, type ServiceHealth, type Transaction } from "@/lib/api";
import { formatINR } from "@/lib/paytrack";
import {
  adminHealthQuery,
  adminKycQuery,
  adminSummaryQuery,
  adminTransactionsQuery,
  adminUsersQuery,
} from "@/lib/queries";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/_authenticated/admin")({
  beforeLoad: () => {
    const user = getUser();
    const isAdmin =
      user &&
      (user.role === "ROLE_ADMIN" ||
        user.role === "ADMIN" ||
        (typeof user.role === "string" && user.role.includes("ADMIN")));
    if (!isAdmin) {
      throw redirect({ to: "/dashboard" });
    }
  },
  head: () => ({
    meta: [
      { title: "Admin Portal — PayTrack" },
      {
        name: "description",
        content: "PayTrack administrative dashboard for system analytics, KYC verification, and user management.",
      },
    ],
  }),
  loader: ({ context }) =>
    Promise.all([
      context.queryClient.ensureQueryData(adminSummaryQuery),
      context.queryClient.ensureQueryData(adminKycQuery()),
      context.queryClient.ensureQueryData(adminUsersQuery()),
      context.queryClient.ensureQueryData(adminTransactionsQuery()),
      context.queryClient.ensureQueryData(adminHealthQuery),
    ]),
  component: AdminPage,
});

function AdminPage() {
  const queryClient = useQueryClient();
  const { data: summary } = useSuspenseQuery(adminSummaryQuery);
  const { data: kycRequests } = useSuspenseQuery(adminKycQuery());
  const { data: users } = useSuspenseQuery(adminUsersQuery());
  const { data: transactionsData } = useSuspenseQuery(adminTransactionsQuery());
  const { data: healthData, refetch: refetchHealth, isFetching: isFetchingHealth } = useSuspenseQuery(adminHealthQuery);

  const [activeTab, setActiveTab] = useState<AdminTab>("overview");

  // KYC filters and reject dialog state
  const [kycFilter, setKycFilter] = useState<string>("ALL");
  const [rejectDialogItem, setRejectDialogItem] = useState<KycItem | null>(null);
  const [rejectRemarks, setRejectRemarks] = useState("");

  // User management search & filter
  const [userSearch, setUserSearch] = useState("");
  const [userStatusFilter, setUserStatusFilter] = useState("ALL");

  // Transaction audit search & filter
  const [txnSearch, setTxnSearch] = useState("");
  const [txnTypeFilter, setTxnTypeFilter] = useState("ALL");
  const [txnStatusFilter, setTxnStatusFilter] = useState("ALL");

  // Mutations
  const reviewKycMutation = useMutation({
    mutationFn: async ({
      id,
      decision,
      remarks,
    }: {
      id: string;
      decision: "APPROVED" | "REJECTED";
      remarks?: string;
    }) => {
      return api.reviewKyc(id, decision, remarks);
    },
    onSuccess: (_, vars) => {
      toast.success(
        vars.decision === "APPROVED" ? "KYC Approved Successfully" : "KYC Rejected",
      );
      setRejectDialogItem(null);
      setRejectRemarks("");
      queryClient.invalidateQueries({ queryKey: ["admin-kyc"] });
      queryClient.invalidateQueries({ queryKey: ["admin-users"] });
      queryClient.invalidateQueries({ queryKey: ["admin-summary"] });
    },
    onError: (err: any) => {
      toast.error(err?.message || "Failed to process KYC review");
    },
  });

  const updateUserStatusMutation = useMutation({
    mutationFn: async ({ userId, status }: { userId: string; status: string }) => {
      return api.updateUserStatus(userId, status);
    },
    onSuccess: (_, vars) => {
      toast.success(`User marked as ${vars.status}`);
      queryClient.invalidateQueries({ queryKey: ["admin-users"] });
    },
    onError: (err: any) => {
      toast.error(err?.message || "Failed to update user status");
    },
  });

  const refreshAll = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["admin-summary"] }),
      queryClient.invalidateQueries({ queryKey: ["admin-kyc"] }),
      queryClient.invalidateQueries({ queryKey: ["admin-users"] }),
      queryClient.invalidateQueries({ queryKey: ["admin-transactions"] }),
      queryClient.invalidateQueries({ queryKey: ["admin-health"] }),
    ]);
    toast.success("Real-time data refreshed");
  };

  // Filtered lists
  const filteredKyc = useMemo(() => {
    if (kycFilter === "ALL") return kycRequests;
    return kycRequests.filter((k) => k.status === kycFilter);
  }, [kycRequests, kycFilter]);

  const pendingKycCount = useMemo(
    () => kycRequests.filter((k) => k.status === "PENDING").length,
    [kycRequests],
  );

  const approvedKycCount = useMemo(
    () => kycRequests.filter((k) => k.status === "APPROVED").length,
    [kycRequests],
  );

  const rejectedKycCount = useMemo(
    () => kycRequests.filter((k) => k.status === "REJECTED").length,
    [kycRequests],
  );

  const activeUsersCount = useMemo(
    () => users.filter((u) => u.active).length,
    [users],
  );

  const suspendedUsersCount = useMemo(
    () => users.filter((u) => !u.active).length,
    [users],
  );

  const filteredUsers = useMemo(() => {
    return users.filter((u) => {
      const matchSearch =
        !userSearch.trim() ||
        u.fullName.toLowerCase().includes(userSearch.toLowerCase()) ||
        u.email.toLowerCase().includes(userSearch.toLowerCase()) ||
        (u.phone && u.phone.includes(userSearch)) ||
        (u.upiId && u.upiId.toLowerCase().includes(userSearch.toLowerCase()));

      const matchStatus =
        userStatusFilter === "ALL" ||
        (userStatusFilter === "ACTIVE" && u.active) ||
        (userStatusFilter === "SUSPENDED" && !u.active);

      return matchSearch && matchStatus;
    });
  }, [users, userSearch, userStatusFilter]);

  const allTxns = transactionsData?.rows || [];
  const filteredTxns = useMemo(() => {
    return allTxns.filter((t) => {
      const matchSearch =
        !txnSearch.trim() ||
        (t.txn_ref && t.txn_ref.toLowerCase().includes(txnSearch.toLowerCase())) ||
        (t.sender_upi_id && t.sender_upi_id.toLowerCase().includes(txnSearch.toLowerCase())) ||
        (t.receiver_upi_id && t.receiver_upi_id.toLowerCase().includes(txnSearch.toLowerCase())) ||
        (t.description && t.description.toLowerCase().includes(txnSearch.toLowerCase()));

      const matchType = txnTypeFilter === "ALL" || t.type === txnTypeFilter;
      const matchStatus = txnStatusFilter === "ALL" || t.status === txnStatusFilter;

      return matchSearch && matchType && matchStatus;
    });
  }, [allTxns, txnSearch, txnTypeFilter, txnStatusFilter]);

  return (
    <AdminShell
      currentTab={activeTab}
      onTabChange={setActiveTab}
      pendingKycCount={pendingKycCount}
      userCount={users.length}
    >
      <div className="space-y-6">
        {/* Top Header */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-border/60 pb-5">
          <div>
            <div className="flex items-center gap-2">
              <span className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                <ShieldCheck className="h-4 w-4 text-highlight-foreground" /> Admin Console
              </span>
              <span className="text-muted-foreground">•</span>
              <span className="text-xs font-medium text-foreground">
                {activeTab === "overview" && "System Overview & Analytics"}
                {activeTab === "kyc" && "KYC Verification Queue"}
                {activeTab === "users" && "User Governance"}
                {activeTab === "transactions" && "Financial Audit Trail"}
                {activeTab === "health" && "Microservices Live Health"}
              </span>
            </div>
            <h1 className="mt-1 font-display text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              {activeTab === "overview" && "Platform Operations & Metrics"}
              {activeTab === "kyc" && "Identity & Document Reviews"}
              {activeTab === "users" && "User Account Governance"}
              {activeTab === "transactions" && "System Transaction Audit"}
              {activeTab === "health" && "Microservices Health Monitor"}
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={refreshAll} className="gap-2 text-xs">
              <RefreshCw className="h-3.5 w-3.5" /> Sync Live Data
            </Button>
          </div>
        </div>

        {/* 1. OVERVIEW TAB */}
        {activeTab === "overview" && (
          <div className="space-y-6">
            {/* Real KPI Cards */}
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Card className="p-5 shadow-card border-border/80">
                <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                  Total Platform Volume
                </p>
                <p className="num mt-2 font-display text-2xl font-bold text-foreground">
                  {formatINR(summary?.totalVolume ?? 0)}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">Settled across all wallets</p>
              </Card>

              <Card className="p-5 shadow-card border-border/80">
                <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                  Total Platform Transactions
                </p>
                <p className="num mt-2 font-display text-2xl font-bold text-foreground">
                  {summary?.totalTransactions ?? 0}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">P2P, recharges, and topups</p>
              </Card>

              <Card className="p-5 shadow-card border-border/80">
                <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                  Pending KYC Requests
                </p>
                <p className="num mt-2 font-display text-2xl font-bold text-warning">
                  {pendingKycCount}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">Requiring manual approval</p>
              </Card>

              <Card className="p-5 shadow-card border-border/80">
                <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                  Registered Accounts
                </p>
                <p className="num mt-2 font-display text-2xl font-bold text-foreground">
                  {users.length}
                </p>
                <p className="mt-1 text-xs text-muted-foreground">
                  {activeUsersCount} active • {suspendedUsersCount} suspended
                </p>
              </Card>
            </div>

            {/* Real Analytics Breakdown Grid */}
            <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
              {/* Channel Volume Breakdown */}
              <Card className="shadow-card border-border/80">
                <CardHeader>
                  <CardTitle className="text-base">Transaction Volume by Type</CardTitle>
                  <CardDescription>Processed volume per financial operation</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  {Object.keys(summary?.volumeByType || {}).length === 0 ? (
                    <p className="py-6 text-center text-sm text-muted-foreground">
                      No transaction volume recorded yet
                    </p>
                  ) : (
                    Object.entries(summary?.volumeByType || {}).map(([type, vol]) => (
                      <div
                        key={type}
                        className="flex items-center justify-between border-b border-border/50 pb-3 last:border-0 last:pb-0"
                      >
                        <div>
                          <p className="font-medium text-foreground text-sm">
                            {type.replace(/_/g, " ")}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {summary?.countByType?.[type] ?? 0} transactions
                          </p>
                        </div>
                        <span className="font-semibold text-foreground text-sm">
                          {formatINR(Number(vol))}
                        </span>
                      </div>
                    ))
                  )}
                </CardContent>
              </Card>

              {/* Real KYC Breakdown */}
              <Card className="shadow-card border-border/80">
                <CardHeader>
                  <CardTitle className="text-base">KYC Verification Status</CardTitle>
                  <CardDescription>Breakdown of government identity submissions</CardDescription>
                </CardHeader>
                <CardContent className="space-y-3">
                  <div className="flex items-center justify-between rounded-lg bg-surface/70 p-3">
                    <div className="flex items-center gap-2.5">
                      <span className="h-2.5 w-2.5 rounded-full bg-warning" />
                      <span className="text-sm font-medium">Pending Review</span>
                    </div>
                    <Badge variant="warning">{pendingKycCount}</Badge>
                  </div>
                  <div className="flex items-center justify-between rounded-lg bg-surface/70 p-3">
                    <div className="flex items-center gap-2.5">
                      <span className="h-2.5 w-2.5 rounded-full bg-success" />
                      <span className="text-sm font-medium">Approved / Verified</span>
                    </div>
                    <Badge variant="success">{approvedKycCount}</Badge>
                  </div>
                  <div className="flex items-center justify-between rounded-lg bg-surface/70 p-3">
                    <div className="flex items-center gap-2.5">
                      <span className="h-2.5 w-2.5 rounded-full bg-destructive" />
                      <span className="text-sm font-medium">Rejected Documents</span>
                    </div>
                    <Badge variant="destructive">{rejectedKycCount}</Badge>
                  </div>
                </CardContent>
              </Card>

              {/* Real Live Microservice Health Overview */}
              <Card className="shadow-card border-border/80">
                <CardHeader className="flex flex-row items-center justify-between">
                  <div>
                    <CardTitle className="text-base">Live Microservices</CardTitle>
                    <CardDescription>Real-time roundtrip API response times</CardDescription>
                  </div>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-8 px-2 text-xs"
                    onClick={() => setActiveTab("health")}
                  >
                    Details
                  </Button>
                </CardHeader>
                <CardContent className="space-y-3">
                  {healthData.map((svc) => (
                    <div
                      key={svc.key}
                      className="flex items-center justify-between rounded-lg bg-surface/70 p-2.5"
                    >
                      <div className="flex items-center gap-2">
                        <span
                          className={cn(
                            "h-2 w-2 rounded-full",
                            svc.status === "UP" ? "bg-success" : "bg-destructive",
                          )}
                        />
                        <span className="text-xs font-medium text-foreground">{svc.name}</span>
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="font-mono text-[11px] text-muted-foreground">
                          {svc.latencyMs}ms
                        </span>
                        <Badge
                          variant={svc.status === "UP" ? "success" : "destructive"}
                          className="text-[10px] py-0 px-1.5"
                        >
                          {svc.status}
                        </Badge>
                      </div>
                    </div>
                  ))}
                </CardContent>
              </Card>
            </div>

            {/* Recent Platform Activity */}
            <Card className="shadow-card border-border/80">
              <CardHeader className="flex flex-row items-center justify-between">
                <div>
                  <CardTitle className="text-base">Recent Platform Transactions</CardTitle>
                  <CardDescription>Live feed of latest financial operations</CardDescription>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setActiveTab("transactions")}
                  className="text-xs"
                >
                  View All Transactions
                </Button>
              </CardHeader>
              <CardContent>
                {allTxns.length === 0 ? (
                  <p className="py-6 text-center text-sm text-muted-foreground">
                    No system transactions found.
                  </p>
                ) : (
                  <div className="overflow-x-auto">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Reference ID</TableHead>
                          <TableHead>Type</TableHead>
                          <TableHead>Counterparty</TableHead>
                          <TableHead>Amount</TableHead>
                          <TableHead>Status</TableHead>
                          <TableHead>Timestamp</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {allTxns.slice(0, 5).map((t) => (
                          <TableRow key={t.id}>
                            <TableCell className="font-mono text-xs font-medium text-foreground">
                              {t.txn_ref || t.id}
                            </TableCell>
                            <TableCell>
                              <Badge variant="outline" className="text-xs">
                                {t.type}
                              </Badge>
                            </TableCell>
                            <TableCell className="text-xs">
                              {t.receiver_upi_id ? (
                                <span>To: {t.receiver_upi_id}</span>
                              ) : t.sender_upi_id ? (
                                <span>From: {t.sender_upi_id}</span>
                              ) : (
                                <span>Wallet Direct</span>
                              )}
                            </TableCell>
                            <TableCell className="font-semibold">{formatINR(t.amount)}</TableCell>
                            <TableCell>
                              <Badge
                                variant={
                                  t.status === "SUCCESS"
                                    ? "success"
                                    : t.status === "FAILED"
                                      ? "destructive"
                                      : "warning"
                                }
                              >
                                {t.status}
                              </Badge>
                            </TableCell>
                            <TableCell className="text-xs text-muted-foreground">
                              {new Date(t.created_at).toLocaleString()}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        )}

        {/* 2. KYC REVIEWS TAB */}
        {activeTab === "kyc" && (
          <Card className="shadow-card border-border/80">
            <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle className="text-lg">KYC Document Verification Queue</CardTitle>
                <CardDescription>
                  Review submitted government identity documents and grant tier-1 wallet limits.
                </CardDescription>
              </div>
              <div className="flex flex-wrap gap-2">
                {(["ALL", "PENDING", "APPROVED", "REJECTED"] as const).map((status) => (
                  <Button
                    key={status}
                    variant={kycFilter === status ? "default" : "outline"}
                    size="sm"
                    onClick={() => setKycFilter(status)}
                    className="text-xs"
                  >
                    {status}
                  </Button>
                ))}
              </div>
            </CardHeader>
            <CardContent>
              {filteredKyc.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-12 text-center">
                  <CheckCircle2 className="h-10 w-10 text-muted-foreground/60" />
                  <p className="mt-2 text-sm font-medium">No KYC requests in this state</p>
                  <p className="text-xs text-muted-foreground">
                    All submitted verification requests are processed.
                  </p>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>User ID</TableHead>
                        <TableHead>Document Type</TableHead>
                        <TableHead>Document Digits</TableHead>
                        <TableHead>Submitted At</TableHead>
                        <TableHead>Status</TableHead>
                        <TableHead>Remarks</TableHead>
                        <TableHead className="text-right">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredKyc.map((kyc) => (
                        <TableRow key={kyc.id}>
                          <TableCell className="font-mono text-xs text-muted-foreground">
                            {kyc.userId}
                          </TableCell>
                          <TableCell>
                            <Badge variant="outline">{kyc.docType}</Badge>
                          </TableCell>
                          <TableCell className="font-mono text-xs">•••• {kyc.docLast4}</TableCell>
                          <TableCell className="text-xs text-muted-foreground">
                            {kyc.submittedAt ? new Date(kyc.submittedAt).toLocaleString() : "—"}
                          </TableCell>
                          <TableCell>
                            <Badge
                              variant={
                                kyc.status === "APPROVED"
                                  ? "success"
                                  : kyc.status === "REJECTED"
                                    ? "destructive"
                                    : "warning"
                              }
                            >
                              {kyc.status}
                            </Badge>
                          </TableCell>
                          <TableCell className="max-w-[200px] truncate text-xs text-muted-foreground">
                            {kyc.remarks || "—"}
                          </TableCell>
                          <TableCell className="text-right">
                            {kyc.status === "PENDING" ? (
                              <div className="flex items-center justify-end gap-2">
                                <Button
                                  size="sm"
                                  variant="default"
                                  className="h-8 gap-1 bg-success hover:bg-success/90 text-white"
                                  disabled={reviewKycMutation.isPending}
                                  onClick={() =>
                                    reviewKycMutation.mutate({ id: kyc.id, decision: "APPROVED" })
                                  }
                                >
                                  <Check className="h-3.5 w-3.5" /> Approve
                                </Button>
                                <Button
                                  size="sm"
                                  variant="outline"
                                  className="h-8 gap-1 text-destructive hover:bg-destructive/10"
                                  disabled={reviewKycMutation.isPending}
                                  onClick={() => {
                                    setRejectDialogItem(kyc);
                                    setRejectRemarks("");
                                  }}
                                >
                                  <XCircle className="h-3.5 w-3.5" /> Reject
                                </Button>
                              </div>
                            ) : (
                              <span className="text-xs text-muted-foreground">Reviewed</span>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>
        )}

        {/* 3. USER MANAGEMENT TAB */}
        {activeTab === "users" && (
          <Card className="shadow-card border-border/80">
            <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle className="text-lg">User Accounts ({filteredUsers.length})</CardTitle>
                <CardDescription>
                  Search user profiles, monitor KYC status, and activate or suspend platform access.
                </CardDescription>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <div className="relative w-64">
                  <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder="Search name, email, phone..."
                    value={userSearch}
                    onChange={(e) => setUserSearch(e.target.value)}
                    className="pl-9 h-9"
                  />
                </div>
                <div className="flex gap-1.5">
                  {(["ALL", "ACTIVE", "SUSPENDED"] as const).map((st) => (
                    <Button
                      key={st}
                      variant={userStatusFilter === st ? "default" : "outline"}
                      size="sm"
                      onClick={() => setUserStatusFilter(st)}
                      className="text-xs h-9"
                    >
                      {st}
                    </Button>
                  ))}
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {filteredUsers.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted-foreground">No users found.</p>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>User</TableHead>
                        <TableHead>Contact / UPI</TableHead>
                        <TableHead>Role</TableHead>
                        <TableHead>KYC Status</TableHead>
                        <TableHead>Account Status</TableHead>
                        <TableHead className="text-right">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredUsers.map((u) => (
                        <TableRow key={u.userId}>
                          <TableCell>
                            <div>
                              <p className="font-medium text-foreground">{u.fullName}</p>
                              <p className="text-xs text-muted-foreground">{u.email}</p>
                            </div>
                          </TableCell>
                          <TableCell className="text-xs">
                            <p className="text-foreground">{u.phone || "—"}</p>
                            <p className="font-mono text-muted-foreground">{u.upiId || "—"}</p>
                          </TableCell>
                          <TableCell>
                            <Badge
                              variant={u.role.includes("ADMIN") ? "accent" : "secondary"}
                              className="text-xs"
                            >
                              {u.role.replace("ROLE_", "")}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            <Badge
                              variant={
                                u.kycStatus === "APPROVED"
                                  ? "success"
                                  : u.kycStatus === "REJECTED"
                                    ? "destructive"
                                    : "warning"
                              }
                            >
                              {u.kycStatus}
                            </Badge>
                          </TableCell>
                          <TableCell>
                            <Badge variant={u.active ? "success" : "destructive"}>
                              {u.active ? "ACTIVE" : "SUSPENDED"}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-right">
                            {u.role.includes("ADMIN") ? (
                              <span className="text-xs text-muted-foreground">Admin Protected</span>
                            ) : u.active ? (
                              <Button
                                size="sm"
                                variant="outline"
                                className="h-8 gap-1 text-destructive hover:bg-destructive/10"
                                disabled={updateUserStatusMutation.isPending}
                                onClick={() =>
                                  updateUserStatusMutation.mutate({
                                    userId: u.userId,
                                    status: "SUSPENDED",
                                  })
                                }
                              >
                                <UserX className="h-3.5 w-3.5" /> Suspend
                              </Button>
                            ) : (
                              <Button
                                size="sm"
                                variant="outline"
                                className="h-8 gap-1 text-success hover:bg-success/10"
                                disabled={updateUserStatusMutation.isPending}
                                onClick={() =>
                                  updateUserStatusMutation.mutate({
                                    userId: u.userId,
                                    status: "ACTIVE",
                                  })
                                }
                              >
                                <UserCheck className="h-3.5 w-3.5" /> Activate
                              </Button>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>
        )}

        {/* 4. ALL TRANSACTIONS AUDIT TAB */}
        {activeTab === "transactions" && (
          <Card className="shadow-card border-border/80">
            <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle className="text-lg">System Transaction Audit ({filteredTxns.length})</CardTitle>
                <CardDescription>
                  Full audit log of all financial operations across every user wallet.
                </CardDescription>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <div className="relative w-64">
                  <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder="Search txn ref, UPI..."
                    value={txnSearch}
                    onChange={(e) => setTxnSearch(e.target.value)}
                    className="pl-9 h-9"
                  />
                </div>
                {/* Type Filter */}
                <select
                  value={txnTypeFilter}
                  onChange={(e) => setTxnTypeFilter(e.target.value)}
                  className="h-9 rounded-md border border-input bg-card px-3 text-xs"
                >
                  <option value="ALL">All Types</option>
                  <option value="P2P_TRANSFER">P2P Transfer</option>
                  <option value="MOBILE_RECHARGE">Mobile Recharge</option>
                  <option value="WALLET_TOPUP">Wallet Topup</option>
                </select>
                {/* Status Filter */}
                <select
                  value={txnStatusFilter}
                  onChange={(e) => setTxnStatusFilter(e.target.value)}
                  className="h-9 rounded-md border border-input bg-card px-3 text-xs"
                >
                  <option value="ALL">All Statuses</option>
                  <option value="SUCCESS">Success</option>
                  <option value="FAILED">Failed</option>
                  <option value="PENDING">Pending</option>
                </select>
              </div>
            </CardHeader>
            <CardContent>
              {filteredTxns.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted-foreground">
                  No matching transactions found.
                </p>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Txn Reference</TableHead>
                        <TableHead>Type</TableHead>
                        <TableHead>Sender UPI</TableHead>
                        <TableHead>Receiver UPI</TableHead>
                        <TableHead>Amount</TableHead>
                        <TableHead>Status</TableHead>
                        <TableHead>Description</TableHead>
                        <TableHead>Created At</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredTxns.map((t) => (
                        <TableRow key={t.id}>
                          <TableCell className="font-mono text-xs">{t.txn_ref || t.id}</TableCell>
                          <TableCell>
                            <Badge variant="outline">{t.type}</Badge>
                          </TableCell>
                          <TableCell className="font-mono text-xs">
                            {t.sender_upi_id || "Wallet"}
                          </TableCell>
                          <TableCell className="font-mono text-xs">
                            {t.receiver_upi_id || "System"}
                          </TableCell>
                          <TableCell className="font-semibold">{formatINR(t.amount)}</TableCell>
                          <TableCell>
                            <Badge
                              variant={
                                t.status === "SUCCESS"
                                  ? "success"
                                  : t.status === "FAILED"
                                    ? "destructive"
                                    : "warning"
                              }
                            >
                              {t.status}
                            </Badge>
                          </TableCell>
                          <TableCell className="max-w-[180px] truncate text-xs text-muted-foreground">
                            {t.description || "—"}
                          </TableCell>
                          <TableCell className="text-xs text-muted-foreground">
                            {new Date(t.created_at).toLocaleString()}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>
        )}

        {/* 5. SERVICES HEALTH TAB */}
        {activeTab === "health" && (
          <div className="space-y-6">
            <Card className="shadow-card border-border/80">
              <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <CardTitle className="text-lg">Microservices Live Health Diagnostics</CardTitle>
                  <CardDescription>
                    Real-time ping probes testing latency, connectivity, and circuit breaker status across all services.
                  </CardDescription>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => refetchHealth()}
                  disabled={isFetchingHealth}
                  className="gap-2 text-xs"
                >
                  <RefreshCw className={cn("h-3.5 w-3.5", isFetchingHealth && "animate-spin")} />
                  {isFetchingHealth ? "Probing Services..." : "Ping Now"}
                </Button>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  {healthData.map((svc) => (
                    <div
                      key={svc.key}
                      className="rounded-xl border border-border/70 bg-card p-4 shadow-sm space-y-3"
                    >
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2.5">
                          <span
                            className={cn(
                              "h-3 w-3 rounded-full",
                              svc.status === "UP" ? "bg-success" : "bg-destructive",
                            )}
                          />
                          <span className="font-semibold text-foreground text-sm">{svc.name}</span>
                        </div>
                        <Badge variant={svc.status === "UP" ? "success" : "destructive"}>
                          {svc.status}
                        </Badge>
                      </div>

                      <div className="space-y-1.5 text-xs text-muted-foreground">
                        <div className="flex items-center justify-between">
                          <span>Endpoint Probe:</span>
                          <span className="font-mono text-foreground">{svc.endpoint}</span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span>Roundtrip Latency:</span>
                          <span className="font-mono font-medium text-foreground">
                            {svc.latencyMs} ms
                          </span>
                        </div>
                        <div className="flex items-center justify-between">
                          <span>Last Checked:</span>
                          <span>{new Date(svc.lastChecked).toLocaleTimeString()}</span>
                        </div>
                        {svc.error && (
                          <div className="mt-2 rounded bg-destructive/10 p-2 text-destructive text-[11px]">
                            {svc.error}
                          </div>
                        )}
                      </div>
                    </div>
                  ))}
                </div>

                <div className="mt-6 rounded-xl border border-border/60 bg-surface/60 p-4">
                  <div className="flex items-center gap-2 text-xs font-semibold text-foreground">
                    <Zap className="h-4 w-4 text-highlight-foreground" /> Infrastructure Topology
                  </div>
                  <div className="mt-2 grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs text-muted-foreground">
                    <div className="rounded-lg bg-card p-2.5 border border-border/50">
                      <p className="font-medium text-foreground">Service Registry</p>
                      <p className="mt-0.5">Eureka Cluster (:8761)</p>
                    </div>
                    <div className="rounded-lg bg-card p-2.5 border border-border/50">
                      <p className="font-medium text-foreground">Message Broker</p>
                      <p className="mt-0.5">RabbitMQ AMQP (:5672)</p>
                    </div>
                    <div className="rounded-lg bg-card p-2.5 border border-border/50">
                      <p className="font-medium text-foreground">Cache Layer</p>
                      <p className="mt-0.5">Redis Distributed Cache (:6379)</p>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        )}
      </div>

      {/* Reject Remarks Dialog */}
      <Dialog
        open={Boolean(rejectDialogItem)}
        onOpenChange={(open) => {
          if (!open) {
            setRejectDialogItem(null);
            setRejectRemarks("");
          }
        }}
      >
        <DialogContent className="sm:max-w-[425px]">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2 text-destructive">
              <AlertCircle className="h-5 w-5" /> Reject KYC Document
            </DialogTitle>
            <DialogDescription>
              Provide an explanation or reason for rejecting this document (e.g. document expired,
              blurred image, mismatched name).
            </DialogDescription>
          </DialogHeader>
          <div className="py-2">
            <Input
              placeholder="Reason for rejection..."
              value={rejectRemarks}
              onChange={(e) => setRejectRemarks(e.target.value)}
              className="mt-2"
            />
          </div>
          <DialogFooter className="gap-2 sm:gap-0">
            <Button
              variant="outline"
              onClick={() => {
                setRejectDialogItem(null);
                setRejectRemarks("");
              }}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={reviewKycMutation.isPending}
              onClick={() => {
                if (rejectDialogItem) {
                  reviewKycMutation.mutate({
                    id: rejectDialogItem.id,
                    decision: "REJECTED",
                    remarks: rejectRemarks || "Document verification rejected",
                  });
                }
              }}
            >
              Confirm Rejection
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AdminShell>
  );
}
