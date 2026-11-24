import { useEffect, useState, type FormEvent } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";

import { AuthShell } from "@/components/auth-shell";
import { Field, FormMessage } from "@/components/field";
import { Button } from "@/components/ui/button";
import { validatePassword } from "@/lib/paytrack";

export const Route = createFileRoute("/reset-password")({
  head: () => ({
    meta: [
      { title: "Choose a new password — PayTrack" },
      { name: "description", content: "Choose a new password for your PayTrack account." },
      { property: "og:title", content: "Choose a new password — PayTrack" },
      { property: "og:description", content: "Finish resetting your PayTrack password." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: ResetPassword,
});

function ResetPassword() {
  const navigate = useNavigate();
  const [ready, setReady] = useState<"checking" | "ok" | "invalid">("checking");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setReady("ok");
  }, []);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    const pwError = validatePassword(password, confirm);
    if (pwError) {
      setError(pwError);
      return;
    }
    setLoading(true);
    setTimeout(async () => {
      setLoading(false);
      await navigate({ to: "/login", replace: true });
    }, 500);
  };

  return (
    <AuthShell title="Choose a new password" subtitle="Use at least 8 characters">
      {ready === "invalid" ? (
        <div className="space-y-4">
          <FormMessage tone="error">This link is invalid or has expired.</FormMessage>
          <Button asChild className="w-full">
            <Link to="/forgot-password">Send a new link</Link>
          </Button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-5" noValidate>
          <Field label="New Password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" required autoComplete="new-password" />
          <Field label="Confirm New Password" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} placeholder="••••••••" required autoComplete="new-password" />
          {error && <FormMessage tone="error">{error}</FormMessage>}
          <Button type="submit" size="lg" className="w-full" loading={loading} disabled={ready !== "ok"}>
            Save password
          </Button>
        </form>
      )}
    </AuthShell>
  );
}
