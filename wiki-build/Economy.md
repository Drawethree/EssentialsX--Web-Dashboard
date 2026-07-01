# Economy

Server-wide money management backed by EssentialsX (and Vault, if installed).

**Permissions:** `ECONOMY_VIEW` to view, `ECONOMY_MANAGE` for bulk operations and balance changes,
`ECONOMY_LOG_VIEW` for the transaction ledger.

## Page (`/economy`)

- **Stats** — total money in circulation, average balance, and number of accounts, with the server's
  currency symbol.
- **Baltop** — paginated leaderboard of the richest players, with **CSV export**.
- **Bulk give / take** — add or remove money from **all** players or only **online** players in one
  action. Every bulk change is audited.
- **Debts** — list players carrying a **negative balance**, and **reset all debts to zero** in one
  click when you need to wipe the slate.
- **Money-supply insights** — a supply trend chart over time plus **top earners** and **top spenders**
  for the selected range (e.g. 24h / 7d / 30d).

> 📸 **Screenshot:** _The Economy page — stats, baltop leaderboard and bulk tools._
> _(See [how to add screenshots](Images).)_

## Transaction ledger

The dashboard records balance changes to its own ledger so you get an audit trail of who changed what
and where it came from (dashboard edit, bulk operation, or in-game source). View the global ledger on
the economy page, or a single player's history from their [[profile|Players]] **Economy** tab.

## Editing a single player's balance

Open a player from [[Players|Players]] → **Economy** tab → set / give / take. Works for offline
players too.

## Related

- [[Players|Players]] — per-player economy and history.
- [[Dashboard & Analytics|Dashboard-Overview-and-Analytics]] — money supply alongside other server
  trends.
- [[Audit Log|Audit-Log]] — every balance change is recorded.
