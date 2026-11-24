import { useState, type FormEvent } from "react";
import { createFileRoute, Link } from "@tanstack/react-router";

import { AuthShell } from "@/components/auth-shell";
import { Field, FormMessage } from "@/components/field";
import { Button } from "@/components/ui/button";
import { supabase } from "@/integrations/supabase/client";

export const Route = createFileRoute("/forgot-password")({
  head: () => ({
    meta: [
      { title: "Reset password — PayTrack" },
      { name: "description", content: "Get a secure link to set a new PayTrack password." },
      { property: "og:title", content: "Reset password — PayTrack" },
      { property: "og:description", content: "Verify your email and choose a new PayTrack password." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: ForgotPassword,
});

function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    if (!email.trim()) {
      setError("Please enter your email");
      return;
    }
    setLoading(true);
    const { error: resetError } = await supabase.auth.resetPasswordForEmail(email.trim(), {
      redirectTo: `${window.location.origin}/reset-password`,
    });
    setLoading(false);
    if (resetError) {
      setError(resetError.message || "Couldn't send the email. Please try again.");
      return;
    }
    setSent(true);
  };

  return (
    <AuthShell
      title="Set a new password"
      subtitle="We'll email you a secure link to verify it's you"
      footer={
        <Link to="/login" className="font-medium text-foreground underline decoration-border-strong underline-offset-4 hover:decoration-foreground">
          Back to sign in
        </Link>
      }
    >
      {sent ? (
        <FormMessage tone="success">
          If an account exists for {email}, a link is on its way. Open it to choose your new password.
        </FormMessage>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-5" noValidate>
          <Field label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" required autoComplete="email" />
          {error && <FormMessage tone="error">{error}</FormMessage>}
          <Button type="submit" size="lg" className="w-full" loading={loading}>
            Send link
          </Button>
        </form>
      )}
    </AuthShell>
  );
}
