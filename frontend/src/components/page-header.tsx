import type { ReactNode } from "react";

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
      <div>
        {eyebrow && <p className="mb-2 text-sm text-muted-foreground">{eyebrow}</p>}
        <h1 className="font-display text-3xl font-semibold tracking-tight text-foreground md:text-[40px]">{title}</h1>
        {description && <p className="mt-2 max-w-xl text-[15px] text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function PageSkeleton() {
  return (
    <div className="animate-pulse space-y-8">
      <div className="space-y-3">
        <div className="h-4 w-32 rounded-full bg-surface" />
        <div className="h-9 w-64 rounded-full bg-surface" />
      </div>
      <div className="h-52 rounded-3xl bg-surface" />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="h-72 rounded-2xl bg-surface" />
        <div className="h-72 rounded-2xl bg-surface" />
      </div>
    </div>
  );
}

/** Small uppercase label used above stats and list sections. */
export function SectionLabel({ children }: { children: ReactNode }) {
  return <p className="text-xs font-medium uppercase tracking-[0.08em] text-subtle-foreground">{children}</p>;
}
