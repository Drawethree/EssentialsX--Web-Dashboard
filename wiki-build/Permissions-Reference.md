# Permissions Reference

Staff permissions are granular and per-feature. **ADMIN** has all of them implicitly; **STAFF** accounts
get only what you grant on the [[Staff page|Staff-Accounts-and-Permissions]]; **DEMO** gets a read-only
subset and is additionally blocked from all non-GET requests.

## All permissions

| Permission | Unlocks | Page |
|---|---|---|
| `PLAYERS_VIEW` | Browse the player list & profiles | [[Players|Players]] |
| `PLAYERS_MANAGE` | Edit players (balance, nickname, homes, mail, gamemode, teleport, etc.) | [[Players|Players]] |
| `ECONOMY_VIEW` | View baltop & economy stats | [[Economy|Economy]] |
| `ECONOMY_MANAGE` | Bulk give/take and balance changes | [[Economy|Economy]] |
| `ECONOMY_LOG_VIEW` | View the transaction ledger | [[Economy|Economy]] |
| `BANS_VIEW` | View bans & mutes lists | [[Bans, Mutes & Warnings|Bans-Mutes-and-Warnings]] |
| `BANS_MANAGE` | Ban/unban/mute/unmute/warn; edit templates & escalation | [[Bans, Mutes & Warnings|Bans-Mutes-and-Warnings]] |
| `KITS_VIEW` | View kits | [[Kits & Warps|Kits-and-Warps]] |
| `KITS_MANAGE` | Create/edit/delete kits | [[Kits & Warps|Kits-and-Warps]] |
| `WARPS_VIEW` | View warps | [[Kits & Warps|Kits-and-Warps]] |
| `WARPS_MANAGE` | Create/edit/delete warps; teleport to a warp | [[Kits & Warps|Kits-and-Warps]] |
| `MAIL_MANAGE` | Send player mail and mail-all | [[Players|Players]] / [[Broadcast & Mail|Tools-Broadcast-and-Mail]] |
| `INVENTORY_VIEW` | View player inventory/ender chest | [[Players|Players]] |
| `INVENTORY_MANAGE` | Edit inventory slots | [[Players|Players]] |
| `CONSOLE_VIEW` | Watch live console output | [[Live Console|Live-Console]] |
| `CONSOLE_EXECUTE` | Run commands from the console | [[Live Console|Live-Console]] |
| `CONFIG_VIEW` | View the EssentialsX config | [[Configuration|Configuration]] (Config page) |
| `CONFIG_MANAGE` | Edit the EssentialsX config | Config page |
| `SERVER_MANAGE` | Whitelist, worlds, save/stop, spawn, jails | [[Server Controls|Server-Controls]] |
| `MODULES_VIEW` | View module status | [[EssentialsX Modules|EssentialsX-Modules]] |
| `MODULES_MANAGE` | Edit Chat/Protect/Discord configs | [[EssentialsX Modules|EssentialsX-Modules]] |
| `SCHEDULER_VIEW` | View scheduled tasks | [[Scheduler|Scheduler]] |
| `SCHEDULER_MANAGE` | Create/edit/delete & run tasks | [[Scheduler|Scheduler]] |
| `CHAT_VIEW` | View live chat & history | [[Chat Moderation|Chat-Moderation]] |
| `CHAT_MODERATE` | Delete chat lines & act on players | [[Chat Moderation|Chat-Moderation]] |
| `BROADCAST` | Send broadcasts | [[Broadcast & Mail|Tools-Broadcast-and-Mail]] |
| `AUDIT_LOG` | View the audit log | [[Audit Log|Audit-Log]] |

> 27 permissions in total.

## Always available

The **Overview**, **Analytics** and **Security** pages are available to any logged-in user regardless of
permissions.

## Admin-only pages

These require the **ADMIN** role and ignore staff permissions:
[[Staff & Permissions|Staff-Accounts-and-Permissions]], [[Branding|Branding]], and the
moderation-settings page (`/moderation`). The read-only **DEMO** account may also *view* these three
pages for evaluation (see below); regular STAFF cannot.

## Demo account permissions

DEMO is a showcase role designed to let prospective users **see almost the entire panel** while being
**strictly read-only** — `JwtMiddleware` blocks every non-GET request for demo accounts, so nothing can
be created, edited, executed or deleted no matter what is granted.

Demo accounts automatically receive view access to every standard feature page:

```
PLAYERS_VIEW, INVENTORY_VIEW, ECONOMY_VIEW, ECONOMY_LOG_VIEW,
BANS_VIEW, KITS_VIEW, WARPS_VIEW, CONSOLE_VIEW, CHAT_VIEW,
CONFIG_VIEW, MODULES_VIEW, SCHEDULER_VIEW, AUDIT_LOG,
SERVER_MANAGE, BROADCAST, MAIL_MANAGE, BANS_MANAGE
```

The four `*_MANAGE`/`BROADCAST` entries are included only because those pages (Server Controls, Tools,
Moderation settings, and ban actions) have no separate "view" permission — they unlock **viewing**
only; the actual write requests are still blocked. Execute-type permissions like `CONSOLE_EXECUTE`,
`PLAYERS_MANAGE`, `CONFIG_MANAGE`, `KITS_MANAGE`, etc. are deliberately **not** granted.

### Privacy: IP masking

For demo viewers, **IP addresses are masked** everywhere they would otherwise appear — player
profiles, GeoIP, login timeline, alt-account detection, active sessions, the audit log, and the live
console stream. Each address is replaced with a stable, non-reversible token (e.g. `ip#a1b2c3`), so
correlations still demonstrate value (two accounts sharing an IP map to the same token) without
revealing the real address. GeoIP country/city is still shown.
