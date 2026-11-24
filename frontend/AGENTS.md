<!-- LOVABLE:BEGIN -->
> [!IMPORTANT]
> This project is connected to [Lovable](https://lovable.dev). Avoid rewriting
> published git history — force pushing, or rebasing/amending/squashing commits
> that are already pushed — as it rewrites history on Lovable's side and the
> user will likely lose their project history.
>
> Commits you push to the connected branch sync back to Lovable and show up in
> the editor, so keep the branch in a working state.

## What Lovable manages

Rules outside this block are the project's own. Change the pieces below in
Lovable, not by hand. With the [Lovable MCP](https://mcp.lovable.dev)
connected, `send_message` asks Lovable to do it; otherwise ask the user to
use the editor.

- **Database.** Do not add or edit `supabase/migrations/` or `drizzle/`.
  Ask Lovable to change the schema.
- **Generated files.** Do not edit a file whose header says it is automatically
  generated.
- **AI gateway.** Keep model calls on Lovable's gateway, and keep
  `LOVABLE_API_KEY` out of browser code.
- **Connectors.** Lovable provides native connectors. `list_connectors` shows
  which are enabled; `add_connector` only returns the dashboard link. When a
  connector is enabled, calls go through the connector gateway and need
  `LOVABLE_API_KEY`. When Lovable does not provide one, users can bring their
  own key and store it in Secrets.
- **Secrets.** Lovable manages them. Do not put secret values in the repo.
<!-- LOVABLE:END -->

## Project rules

- Money moves (top-up, transfer, recharge) run only inside SECURITY DEFINER database functions that read `auth.uid()`; wallets, ledger and transactions have no client write grants. Why: balance changes must be atomic and impossible to forge from the browser.
- Business limits live in `src/lib/paytrack.ts` for the UI and are re-checked inside the database functions; change both together and keep `src/lib/paytrack.test.ts` in sync. Why: the UI check is for messages, the database check is the real guard.
- Signed-in pages live under `src/routes/_authenticated/` (client-only, session check in `beforeLoad`); data is read in the browser through the Cloud client with RLS. Why: sessions are stored in the browser, so SSR cannot see them.
- Signup creates the profile and wallet via the `handle_new_user` trigger; UPI ID is derived from the phone (`<phone>@paytrack`) as a generated column. Why: every user always has exactly one wallet and a consistent UPI ID.
- Visual tokens (colours, fonts, radii, shadows) live only in `src/styles.css`; money amounts use the `num` utility (tabular figures) and shared rows/cards come from `src/components/activity.tsx` and `balance-card.tsx`. Why: one place to restyle and consistent amounts across pages.
