import { useEffect, useState, type FormEvent } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";

import { AuthShell } from "@/components/auth-shell";
import { Field, FormMessage } from "@/components/field";
import { Button } from "@/components/ui/button";
import { supabase } from "@/integrations/supabase/client";

type LoginSearch = { redirect?: string; registered?: boolean };

function safeRedirect(value: unknown): string | undefined {
  return typeof value === "string" && value.startsWith("/") && !value.startsWith("//")
    ? value
    : undefined;
}

export const Route = createFileRoute("/login")({
  validateSearch: (search: Record<string, unknown>): LoginSearch => {
    const out: LoginSearch = {};
    const redirect = safeRedirect(search["redirect"]);
    if (redirect) out.redirect = redirect;
    if (search["registered"] === true || search["registered"] === "true") out.registered = true;
    return out;
  },
  head: () => ({
    meta: [
      { title: "Sign in — PayTrack" },
      { name: "description", content: "Sign in to your PayTrack wallet." },
      { property: "og:title", content: "Sign in — PayTrack" },
      { property: "og:description", content: "Sign in to your PayTrack digital wallet." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Login,
});

function Login() {
  const { redirect, registered } = Route.useSearch();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const target = redirect ?? "/dashboard";

  useEffect(() => {
    void supabase.auth.getSession().then(({ data }) => {
      if (data.session) void navigate({ to: target, replace: true });
    });
  }, [navigate, target]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    const { error: signInError } = await supabase.auth.signInWithPassword({ email, password });
    setLoading(false);
    if (signInError) {
      const msg = signInError.message.toLowerCase();
      setError(
        msg.includes("confirm")
          ? "Please confirm your email first — check your inbox for the link."
          : "Login failed. Please check your credentials.",
      );
      return;
    }
    await navigate({ to: target, replace: true });
  };

  return (
    <AuthShell
      title="Welcome back"
      subtitle="Sign in to your PayTrack account"
      footer={
        <>
          Don't have an account?{" "}
          <Link to="/register" className="font-medium text-foreground underline decoration-border-strong underline-offset-4 hover:decoration-foreground">
            Create one
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        {registered && (
          <FormMessage tone="success">
            Account created! Check your email to confirm it, then sign in.
          </FormMessage>
        )}
        <Field
          label="Email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="you@example.com"
          required
          autoComplete="email"
          error={error ? "Invalid credentials" : null}
        />
        <div>
          <Field
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            required
            autoComplete="current-password"
          />
          <div className="mt-2 text-right">
            <Link to="/forgot-password" className="text-xs font-medium text-muted-foreground hover:text-foreground">
              Forgot password?
            </Link>
          </div>
        </div>
        {error && <FormMessage tone="error">{error}</FormMessage>}
        <Button type="submit" size="lg" className="w-full" loading={loading}>
          Sign in
        </Button>
        <p className="text-center text-xs text-subtle-foreground">
          Had a PayTrack account before the upgrade?{" "}
          <Link to="/forgot-password" className="font-medium text-foreground underline decoration-border-strong underline-offset-4">
            Verify your email and set a new password
          </Link>
        </p>
      </form>
    </AuthShell>
  );
}
