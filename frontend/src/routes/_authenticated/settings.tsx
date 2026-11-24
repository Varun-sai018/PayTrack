import { useEffect, useState, type FormEvent } from "react";
import { createFileRoute } from "@tanstack/react-router";
import { useMutation, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { Check, ShieldCheck } from "lucide-react";
import { toast } from "sonner";

import { Field, FormMessage, NativeSelect } from "@/components/field";
import { PageHeader, PageSkeleton } from "@/components/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { api, getUser } from "@/lib/api";
import { friendlyError, isValidIndianMobile, upiFromPhone, validatePassword } from "@/lib/paytrack";
import { profileQuery } from "@/lib/queries";

export const Route = createFileRoute("/_authenticated/settings")({
  head: () => ({
    meta: [
      { title: "Settings — PayTrack" },
      { name: "description", content: "Manage your PayTrack profile, password and preferences." },
      { property: "og:title", content: "Settings — PayTrack" },
      { property: "og:description", content: "Profile, security and notification preferences." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(profileQuery),
  pendingComponent: PageSkeleton,
  component: SettingsPage,
});


function SettingsPage() {
  const queryClient = useQueryClient();
  const { data: profile } = useSuspenseQuery(profileQuery);
  const [form, setForm] = useState({ fullName: profile?.full_name ?? "", phone: profile?.phone ?? "" });
  const [profileError, setProfileError] = useState("");
  const [saved, setSaved] = useState(false);
  const [pw, setPw] = useState({ current: "", next: "", confirm: "" });
  const [pwError, setPwError] = useState("");
  const [pwSaved, setPwSaved] = useState(false);

  useEffect(() => {
    setForm({ fullName: profile?.full_name ?? "", phone: profile?.phone ?? "" });
  }, [profile?.full_name, profile?.phone]);

  const saveProfile = useMutation({
    mutationFn: async () => {
      // Profile saved locally for current user
      const user = getUser();
      if (user) {
        user.fullName = form.fullName;
        if (form.phone) user.phone = form.phone;
        localStorage.setItem("paytrack_user", JSON.stringify(user));
      }
    },
    onSuccess: async () => {
      setSaved(true);
      window.setTimeout(() => setSaved(false), 3000);
      await queryClient.invalidateQueries({ queryKey: ["profile"] });
    },
    onError: (err) => setProfileError(friendlyError(err, "Couldn't save your profile")),
  });

  const changePassword = useMutation({
    mutationFn: async () => {
      // In demo mode password is mock updated
      return true;
    },
    onSuccess: () => {
      setPw({ current: "", next: "", confirm: "" });
      setPwSaved(true);
      window.setTimeout(() => setPwSaved(false), 3000);
    },
    onError: (err) => setPwError(friendlyError(err, "Couldn't update your password")),
  });

  const submitProfile = (e: FormEvent) => {
    e.preventDefault();
    setProfileError("");
    if (!form.fullName.trim()) return setProfileError("Full name is required");
    if (form.phone && !isValidIndianMobile(form.phone)) {
      return setProfileError("Please enter a valid 10-digit Indian mobile number (starts with 6-9)");
    }
    saveProfile.mutate();
  };

  const submitPassword = (e: FormEvent) => {
    e.preventDefault();
    setPwError("");
    const problem = validatePassword(pw.next, pw.confirm);
    if (problem) return setPwError(problem.replace("Passwords", "New passwords").replace("Password", "New password"));
    changePassword.mutate();
  };

  const initial = (profile?.full_name?.charAt(0) || profile?.email?.charAt(0) || "U").toUpperCase();
  const upi = upiFromPhone(form.phone && isValidIndianMobile(form.phone) ? form.phone : null);

  return (
    <div className="max-w-3xl animate-fade-in space-y-6">
      <PageHeader title="Settings" description="Your profile, password and preferences." />

      <Tabs defaultValue="profile" className="space-y-6">
        <TabsList>
          <TabsTrigger value="profile">Profile</TabsTrigger>
          <TabsTrigger value="security">Security</TabsTrigger>
          <TabsTrigger value="preferences">Preferences</TabsTrigger>
        </TabsList>
        <Card className="p-0">

          <TabsContent value="profile" className="m-0 p-6 sm:p-8">
            <form onSubmit={submitProfile} className="space-y-6">
              <div className="flex items-center gap-6">
                <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-highlight">
                  <span className="font-display text-2xl font-semibold text-highlight-foreground">{initial}</span>
                </div>
                <div>
                  <h2 className="font-display text-xl font-semibold text-foreground">{profile?.full_name || "User"}</h2>
                  <p className="text-sm text-muted-foreground">{profile?.email}</p>
                  <p className="mt-1 text-xs text-subtle-foreground">
                    Member since {new Date(profile?.created_at ?? Date.now()).toLocaleDateString("en-US", { month: "long", year: "numeric" })}
                  </p>
                </div>
              </div>

              <div className="space-y-4 border-t border-border pt-6">
                <h3 className="font-display text-base font-semibold text-foreground">Personal Information</h3>
                <Field label="Full Name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} placeholder="John Doe" required />
                <Field label="Email" type="email" value={profile?.email ?? ""} disabled hint="Email cannot be changed. Contact support if needed." readOnly />
                <Field
                  label="Mobile Number"
                  type="tel"
                  inputMode="numeric"
                  value={form.phone}
                  onChange={(e) => setForm({ ...form, phone: e.target.value })}
                  placeholder="9876543210"
                  hint={upi ? `Your UPI ID: ${upi}` : "Add a 10-digit mobile number to get your @paytrack UPI ID"}
                />
                {profileError && <FormMessage tone="error">{profileError}</FormMessage>}
                <div className="flex gap-3 pt-4">
                  <Button type="submit" loading={saveProfile.isPending}>Save Changes</Button>
                  {saved && (
                    <span className="flex items-center text-sm text-success">
                      <Check className="mr-1 h-4 w-4" aria-hidden="true" /> Saved!
                    </span>
                  )}
                </div>
              </div>
            </form>
          </TabsContent>

          <TabsContent value="security" className="m-0 space-y-8 p-6 sm:p-8">
            <div>
              <h3 className="mb-4 font-display text-base font-semibold text-foreground">Change Password</h3>
              <form onSubmit={submitPassword} className="space-y-4">
                <Field label="Current Password" type="password" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} placeholder="••••••••" required autoComplete="current-password" />
                <Field label="New Password" type="password" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} placeholder="••••••••" required hint="At least 8 characters" autoComplete="new-password" />
                <Field label="Confirm New Password" type="password" value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} placeholder="••••••••" required autoComplete="new-password" />
                {pwError && <FormMessage tone="error">{pwError}</FormMessage>}
                {pwSaved && <FormMessage tone="success">Password updated.</FormMessage>}
                <Button type="submit" loading={changePassword.isPending}>Update Password</Button>
              </form>
            </div>

            <div className="border-t border-border pt-8">
              <h3 className="mb-4 font-display text-base font-semibold text-foreground">Active Sessions</h3>
              <div className="overflow-hidden rounded-xl border border-border">
                <div className="flex items-center justify-between border-b border-border p-4">
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-full bg-success/12">
                      <ShieldCheck className="h-5 w-5 text-success" aria-hidden="true" />
                    </div>
                    <div>
                      <p className="font-medium text-foreground">Current Session</p>
                      <p className="text-xs text-muted-foreground">This device • Active now</p>
                    </div>
                  </div>
                  <Badge variant="success">Current</Badge>
                </div>
                <div className="p-4 text-center text-sm text-muted-foreground">No other active sessions</div>
              </div>
            </div>

            <div className="border-t border-border pt-8">
              <h3 className="mb-4 font-display text-base font-semibold text-foreground">Danger Zone</h3>
              <div className="rounded-xl border border-destructive/25 bg-destructive/5 p-4">
                <div className="flex items-center justify-between gap-4">
                  <div>
                    <p className="font-medium text-foreground">Delete Account</p>
                    <p className="mt-1 text-sm text-muted-foreground">
                      Permanently delete your account and all associated data. This action cannot be undone.
                    </p>
                  </div>
                  <Button variant="destructive" size="sm" onClick={() => toast.info("Please contact support to delete your account.")}>
                    Delete Account
                  </Button>
                </div>
              </div>
            </div>
          </TabsContent>

          <TabsContent value="preferences" className="m-0 space-y-8 p-6 sm:p-8">
            <div>
              <h3 className="mb-4 font-display text-base font-semibold text-foreground">Notifications</h3>
              <div className="overflow-hidden rounded-xl border border-border">
                {[
                  { title: "Transaction Alerts", desc: "Get notified for all transactions", enabled: true },
                  { title: "Expense Reminders", desc: "Weekly summary of your expenses", enabled: true },
                  { title: "Security Alerts", desc: "Login attempts and security changes", enabled: true },
                  { title: "Marketing Emails", desc: "Product updates and tips", enabled: false },
                ].map((n, i) => (
                  <div key={n.title} className={`flex items-center justify-between p-4 ${i > 0 ? "border-t border-border" : ""}`}>
                    <div>
                      <p className="font-medium text-foreground">{n.title}</p>
                      <p className="text-sm text-muted-foreground">{n.desc}</p>
                    </div>
                    <Switch defaultChecked={n.enabled} aria-label={n.title} />
                  </div>
                ))}
              </div>
            </div>

            <div className="border-t border-border pt-8">
              <h3 className="mb-4 font-display text-base font-semibold text-foreground">Currency</h3>
              <div className="rounded-xl border border-border p-4">
                <label htmlFor="currency" className="mb-3 block text-sm font-medium text-foreground">Default Currency</label>
                <NativeSelect id="currency" defaultValue="INR">
                  <option value="INR">INR - Indian Rupee (₹)</option>
                  <option value="USD">USD - US Dollar ($)</option>
                  <option value="EUR">EUR - Euro (€)</option>
                  <option value="GBP">GBP - British Pound (£)</option>
                  <option value="JPY">JPY - Japanese Yen (¥)</option>
                </NativeSelect>
              </div>
            </div>
          </TabsContent>
        </Card>
      </Tabs>
    </div>
  );
}
