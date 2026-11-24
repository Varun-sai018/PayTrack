import { useState, type FormEvent } from "react";
import { Link } from "@tanstack/react-router";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  AlertCircle,
  ArrowRight,
  CheckCircle2,
  Clock,
  FileCheck,
  ShieldAlert,
  ShieldCheck,
  UploadCloud,
} from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { api, type Profile } from "@/lib/api";
import { cn } from "@/lib/utils";

interface KycSectionProps {
  profile: Profile | null;
}

export function KycSection({ profile }: KycSectionProps) {
  const queryClient = useQueryClient();
  const [docType, setDocType] = useState<"AADHAAR" | "PAN" | "VOTER_ID">("AADHAAR");
  const [docNumber, setDocNumber] = useState("");
  const [error, setError] = useState("");

  const kycStatus = profile?.kyc_status || "NOT_SUBMITTED";

  const submitKycMutation = useMutation({
    mutationFn: async () => {
      setError("");
      const cleanNum = docNumber.trim().toUpperCase();

      if (docType === "AADHAAR") {
        if (!/^\d{12}$/.test(cleanNum)) {
          throw new Error("Aadhaar number must be exactly 12 numeric digits");
        }
      } else if (docType === "PAN") {
        if (!/^[A-Z0-9]{10}$/.test(cleanNum)) {
          throw new Error("PAN number must be 10 alphanumeric characters (e.g. ABCDE1234F)");
        }
      } else if (docType === "VOTER_ID") {
        if (cleanNum.length < 6 || cleanNum.length > 12) {
          throw new Error("Voter ID must be between 6 and 12 alphanumeric characters");
        }
      }

      return api.submitKyc(docType, cleanNum);
    },
    onSuccess: async () => {
      toast.success("KYC document submitted successfully. Under review by compliance.");
      setDocNumber("");
      // Update locally & invalidate
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["profile"] }),
        queryClient.invalidateQueries({ queryKey: ["kyc-status"] }),
      ]);
    },
    onError: (err: any) => {
      setError(err?.message || "Failed to submit KYC document");
    },
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    submitKycMutation.mutate();
  };

  return (
    <div className="space-y-6">
      {/* Current Status Card */}
      <Card className="border-border/80 shadow-sm">
        <CardHeader>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <ShieldCheck className="h-5 w-5 text-highlight-foreground" />
              <CardTitle className="text-lg">KYC Identity Verification</CardTitle>
            </div>
            <Badge
              variant={
                kycStatus === "APPROVED"
                  ? "success"
                  : kycStatus === "PENDING"
                    ? "warning"
                    : kycStatus === "REJECTED"
                      ? "destructive"
                      : "secondary"
              }
              className="text-xs px-2.5 py-0.5"
            >
              {kycStatus === "APPROVED" && "KYC VERIFIED"}
              {kycStatus === "PENDING" && "UNDER REVIEW"}
              {kycStatus === "REJECTED" && "REJECTED"}
              {kycStatus === "NOT_SUBMITTED" && "NOT SUBMITTED"}
            </Badge>
          </div>
          <CardDescription>
            Government-mandated identity verification unlocks tier-1 wallet limits and high-volume UPI transactions.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {kycStatus === "APPROVED" && (
            <div className="flex items-start gap-4 rounded-xl bg-success/10 border border-success/20 p-4">
              <CheckCircle2 className="h-5 w-5 text-success shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold text-sm text-foreground">Identity Verified (Tier 1 Active)</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  Your identity has been confirmed by our compliance team. You have full access to wallet top-ups, instant P2P transfers, and mobile recharges without restrictions.
                </p>
              </div>
            </div>
          )}

          {kycStatus === "PENDING" && (
            <div className="flex items-start gap-4 rounded-xl bg-warning/10 border border-warning/20 p-4">
              <Clock className="h-5 w-5 text-warning shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold text-sm text-foreground">Verification in Progress</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  Your government document has been submitted and is currently being reviewed by our administrators. Approval usually takes a few minutes.
                </p>
              </div>
            </div>
          )}

          {kycStatus === "REJECTED" && (
            <div className="flex items-start gap-4 rounded-xl bg-destructive/10 border border-destructive/20 p-4">
              <AlertCircle className="h-5 w-5 text-destructive shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold text-sm text-destructive">Verification Unsuccessful</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  Your previous document submission could not be verified. Please review your details and submit a valid government ID below.
                </p>
              </div>
            </div>
          )}

          {kycStatus === "NOT_SUBMITTED" && (
            <div className="flex items-start gap-4 rounded-xl bg-surface border border-border p-4">
              <ShieldAlert className="h-5 w-5 text-warning shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold text-sm text-foreground">Verification Required</p>
                <p className="mt-0.5 text-xs text-muted-foreground">
                  To comply with RBI guidelines, complete your identity verification by submitting your Aadhaar, PAN, or Voter ID below.
                </p>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Submission Form (Shown when NOT_SUBMITTED or REJECTED) */}
      {(kycStatus === "NOT_SUBMITTED" || kycStatus === "REJECTED") && (
        <Card className="border-border/80 shadow-sm">
          <CardHeader>
            <CardTitle className="text-base flex items-center gap-2">
              <UploadCloud className="h-4 w-4 text-highlight-foreground" />
              {kycStatus === "REJECTED" ? "Re-submit Government Document" : "Submit Identity Document"}
            </CardTitle>
            <CardDescription>
              Select an official document and provide the document number for instant verification.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-5">
              <div className="space-y-2">
                <Label htmlFor="docType" className="text-xs font-medium">
                  Document Type
                </Label>
                <select
                  id="docType"
                  value={docType}
                  onChange={(e) => {
                    setDocType(e.target.value as any);
                    setError("");
                  }}
                  className="w-full rounded-md border border-input bg-card px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                >
                  <option value="AADHAAR">Aadhaar Card (12 Numeric Digits)</option>
                  <option value="PAN">PAN Card (10 Alphanumeric Characters)</option>
                  <option value="VOTER_ID">Voter ID (EPIC Number)</option>
                </select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="docNumber" className="text-xs font-medium">
                  Document Number
                </Label>
                <Input
                  id="docNumber"
                  placeholder={
                    docType === "AADHAAR"
                      ? "123456789012"
                      : docType === "PAN"
                        ? "ABCDE1234F"
                        : "ABC1234567"
                  }
                  value={docNumber}
                  onChange={(e) => {
                    setDocNumber(e.target.value);
                    setError("");
                  }}
                  className="font-mono"
                  required
                />
                <p className="text-[11px] text-muted-foreground">
                  {docType === "AADHAAR" && "Enter your 12-digit Unique Identification Number without spaces."}
                  {docType === "PAN" && "Enter your 10-character Permanent Account Number in uppercase."}
                  {docType === "VOTER_ID" && "Enter your 10-character Electoral Photo Identity Card (EPIC) number."}
                </p>
              </div>

              {error && (
                <div className="rounded-lg bg-destructive/10 border border-destructive/20 p-3 text-xs text-destructive">
                  {error}
                </div>
              )}

              <Button
                type="submit"
                disabled={submitKycMutation.isPending || !docNumber.trim()}
                className="w-full sm:w-auto gap-2"
              >
                <FileCheck className="h-4 w-4" />
                {submitKycMutation.isPending ? "Submitting for Review..." : "Submit for Verification"}
              </Button>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  );
}

/**
 * Noticeable alert banner for Dashboard & Wallet pages prompting users who need KYC.
 */
export function KycBanner({
  kycStatus,
  onOpenDialog,
}: {
  kycStatus?: string | null;
  onOpenDialog?: () => void;
}) {
  if (kycStatus === "APPROVED") return null;

  const isPending = kycStatus === "PENDING";
  const isRejected = kycStatus === "REJECTED";

  return (
    <div
      className={cn(
        "rounded-2xl border p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-sm transition-all",
        isPending
          ? "bg-warning/10 border-warning/30"
          : isRejected
            ? "bg-destructive/10 border-destructive/30"
            : "bg-surface border-border",
      )}
    >
      <div className="flex items-center gap-3">
        <div
          className={cn(
            "flex h-9 w-9 shrink-0 items-center justify-center rounded-xl",
            isPending
              ? "bg-warning/20 text-warning"
              : isRejected
                ? "bg-destructive/20 text-destructive"
                : "bg-highlight text-highlight-foreground",
          )}
        >
          {isPending ? (
            <Clock className="h-4 w-4" />
          ) : isRejected ? (
            <AlertCircle className="h-4 w-4" />
          ) : (
            <ShieldAlert className="h-4 w-4" />
          )}
        </div>
        <div>
          <p className="text-sm font-semibold text-foreground">
            {isPending
              ? "KYC Under Review"
              : isRejected
                ? "KYC Verification Rejected"
                : "Complete KYC Verification"}
          </p>
          <p className="text-xs text-muted-foreground">
            {isPending
              ? "Your document is pending admin approval. You will receive access once verified."
              : isRejected
                ? "Your document was rejected. Please re-submit to activate wallet transfers."
                : "Verify your identity to unlock wallet top-ups, P2P transfers, and bill payments."}
          </p>
        </div>
      </div>

      <div className="flex items-center gap-2 self-end sm:self-auto shrink-0">
        {onOpenDialog ? (
          <Button size="sm" variant={isPending ? "outline" : "default"} onClick={onOpenDialog} className="text-xs gap-1.5">
            {isPending ? "View Status" : "Verify Now"} <ArrowRight className="h-3.5 w-3.5" />
          </Button>
        ) : (
          <Button size="sm" variant={isPending ? "outline" : "default"} asChild className="text-xs gap-1.5">
            <Link to="/settings">
              {isPending ? "View Status" : "Verify Now"} <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </Button>
        )}
      </div>
    </div>
  );
}

/**
 * Modal dialog allowing users to submit KYC from anywhere (Wallet, Dashboard, Transfer).
 */
export function KycDialog({
  open,
  onOpenChange,
  profile,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  profile: Profile | null;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5 text-highlight-foreground" /> KYC Verification
          </DialogTitle>
          <DialogDescription>
            Submit your government identity document to enable transactions and wallet limits.
          </DialogDescription>
        </DialogHeader>
        <div className="py-2">
          <KycSection profile={profile} />
        </div>
      </DialogContent>
    </Dialog>
  );
}
