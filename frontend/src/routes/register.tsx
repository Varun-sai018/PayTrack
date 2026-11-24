import { useState, type ChangeEvent, type FormEvent } from "react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";

import { AuthShell } from "@/components/auth-shell";
import { Field, FormMessage } from "@/components/field";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";
import { friendlyError, isValidIndianMobile, validatePassword } from "@/lib/paytrack";

export const Route = createFileRoute("/register")({
  head: () => ({
    meta: [
      { title: "Create account — PayTrack" },
      { name: "description", content: "Create a PayTrack wallet with your own @paytrack UPI ID." },
      { property: "og:title", content: "Create account — PayTrack" },
      { property: "og:description", content: "Open a PayTrack wallet in under a minute." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Register,
});

function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    fullName: "",
    email: "",
    phone: "",
    password: "",
    confirmPassword: "",
  });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const onChange = (e: ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError("");
    if (!form.fullName.trim()) {
      setError("Please enter your full name");
      return;
    }
    if (!isValidIndianMobile(form.phone)) {
      setError("Please enter a valid 10-digit Indian mobile number (starts with 6-9)");
      return;
    }
    const pwError = validatePassword(form.password, form.confirmPassword);
    if (pwError) {
      setError(pwError);
      return;
    }

    setLoading(true);
    try {
      await api.register({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        password: form.password,
      });
      setLoading(false);
      // Immediately navigate to dashboard
      await navigate({ to: "/dashboard", replace: true });
    } catch (err: any) {
      setLoading(false);
      setError(friendlyError(err, "Registration failed. Please check your details."));
    }
  };

  return (
    <AuthShell
      title="Create your account"
      subtitle="Start managing your finances with PayTrack"
      footer={
        <>
          Already have an account?{" "}
          <Link to="/login" className="font-medium text-foreground underline decoration-border-strong underline-offset-4 hover:decoration-foreground">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        <Field label="Full Name" name="fullName" value={form.fullName} onChange={onChange} placeholder="John Doe" required autoComplete="name" />
        <Field label="Email" name="email" type="email" value={form.email} onChange={onChange} placeholder="you@example.com" required autoComplete="email" />
        <Field
          label="Mobile Number"
          name="phone"
          type="tel"
          inputMode="numeric"
          value={form.phone}
          onChange={onChange}
          placeholder="9876543210"
          required
          autoComplete="tel"
          hint="10-digit number used for UPI ID (e.g. 9876543210@paytrack)"
        />
        <Field label="Password" name="password" type="password" value={form.password} onChange={onChange} placeholder="••••••••" required autoComplete="new-password" hint="At least 8 characters" />
        <Field label="Confirm Password" name="confirmPassword" type="password" value={form.confirmPassword} onChange={onChange} placeholder="••••••••" required autoComplete="new-password" />
        {error && <FormMessage tone="error">{error}</FormMessage>}
        <Button type="submit" size="lg" className="w-full" loading={loading}>
          Create account
        </Button>
      </form>
    </AuthShell>
  );
}
