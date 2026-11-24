import { createFileRoute, redirect } from "@tanstack/react-router";

// The original app sent "/" straight to the dashboard; signed-out visitors
// are then sent on to sign in by the protected layout.
export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "PayTrack — Digital Wallet" },
      {
        name: "description",
        content: "Your PayTrack wallet: UPI transfers, mobile recharges and expense tracking.",
      },
      { property: "og:title", content: "PayTrack — Digital Wallet" },
      {
        property: "og:description",
        content: "Send money over UPI, recharge your mobile and track every rupee you spend.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  beforeLoad: () => {
    throw redirect({ to: "/dashboard", replace: true });
  },
});
