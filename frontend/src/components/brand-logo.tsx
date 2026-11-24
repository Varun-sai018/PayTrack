import { cn } from "@/lib/utils";

export function BrandMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" className={cn("h-8 w-8", className)}>
      <rect width="32" height="32" rx="9" className="fill-ink" />
      <path
        d="M11 23V9.5h5.5a4.5 4.5 0 0 1 0 9H11"
        className="stroke-highlight"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function BrandLogo({ className }: { className?: string }) {
  return (
    <span className={cn("flex items-center gap-2.5 font-display text-[19px] font-semibold tracking-tight text-foreground", className)}>
      <BrandMark />
      <span>PayTrack</span>
    </span>
  );
}
