import * as React from "react";

import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

type FieldProps = React.ComponentProps<"input"> & {
  label: string;
  hint?: string;
  error?: string | null;
};

export const Field = React.forwardRef<HTMLInputElement, FieldProps>(
  ({ label, hint, error, id: providedId, className, ...props }, ref) => {
    const generatedId = React.useId();
    const id = providedId ?? generatedId;
    const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
    return (
      <div className={cn("w-full", className)}>
        <label htmlFor={id} className="mb-1.5 block text-[13px] font-medium text-muted-foreground">
          {label}
        </label>
        <Input
          ref={ref}
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          {...props}
        />
        {error ? (
          <p id={`${id}-error`} className="mt-1.5 text-xs text-destructive" role="alert">
            {error}
          </p>
        ) : hint ? (
          <p id={`${id}-hint`} className="mt-1.5 text-xs text-subtle-foreground">
            {hint}
          </p>
        ) : null}
      </div>
    );
  },
);
Field.displayName = "Field";

export const NativeSelect = React.forwardRef<HTMLSelectElement, React.ComponentProps<"select">>(
  ({ className, ...props }, ref) => (
    <select
      ref={ref}
      className={cn(
        "h-11 w-full cursor-pointer appearance-none rounded-xl border border-input bg-card bg-[length:16px] bg-[right_0.85rem_center] bg-no-repeat pl-4 pr-10 text-[15px] text-foreground transition-all hover:border-border-strong focus:border-foreground focus:outline-none focus:ring-4 focus:ring-foreground/8 [background-image:var(--select-chevron)]",
        className,
      )}
      {...props}
    />
  ),
);
NativeSelect.displayName = "NativeSelect";

export function FormMessage({ tone, children }: { tone: "error" | "success"; children: React.ReactNode }) {
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={cn(
        "rounded-xl px-3.5 py-3 text-sm",
        tone === "error"
          ? "bg-destructive/8 text-destructive"
          : "bg-success/10 text-success",
      )}
    >
      {children}
    </div>
  );
}
