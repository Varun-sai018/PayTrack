import { Badge } from "@/components/ui/badge";

export function StatusBadge({ status }: { status: string | null | undefined }) {
  const s = status?.toLowerCase();
  const variant =
    s === "completed" || s === "success" || s === "settled"
      ? "success"
      : s === "pending"
        ? "warning"
        : s === "failed"
          ? "destructive"
          : "default";
  return <Badge variant={variant}>{status}</Badge>;
}
