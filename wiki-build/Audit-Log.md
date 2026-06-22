# Audit Log

Every meaningful action taken through the dashboard is recorded, so you always know **who did what,
and when**.

**Permission:** `AUDIT_LOG`.

## Page (`/audit-log`)

- Table of entries: timestamp, acting user, action, and details.
- **Search** by user / action / details (debounced).
- **Filter** by action type.
- Relative times (e.g. "2 minutes ago") with the full timestamp on hover.
- Action **badges** are color-coded by severity.
- **CSV export** of the current page or all entries.

## What gets logged

Logins and failures, 2FA changes, session revocations, account/credential changes, player edits
(balance, nickname, gamemode, inventory), bans/mutes/warns and their reversals, kicks, mail, console
commands (including **blocked** attempts), broadcasts and mail-all, kit/warp changes, config saves,
module config saves, staff management, scheduled-task changes, whitelist/world/spawn/save/stop actions,
and branding updates.

Badges are grouped by severity — destructive/security-negative events (e.g. `BAN`, `KICK`,
`SERVER_STOP`, `LOGIN_FAIL`, `STAFF_DELETE`) in red, caution events (config edits, privilege changes,
`MUTE`, bulk economy) in amber, and positive/reversal events (`UNBAN`, `UNMUTE`, `LOGIN`,
`2FA_ENABLE`) in green.

## Storage & rotation

The audit trail is written to a file (`plugins/EssDashboard/audit.log`). It rotates when it grows past
**5 MB**, keeping up to **3 older backups** (`audit.log.1`, `.2`, `.3`).

## Discord notifications

Audited actions can be forwarded to a Discord channel via webhook. Configure
`notifications.webhook-url` and pick which actions notify via `notifications.events` in
[[Configuration|Configuration]] (defaults: `BAN`, `UNBAN`, `KICK`, `MUTE`, `LOGIN_FAIL`,
`SERVER_STOP`).

## Related

- [[Configuration|Configuration]] — webhook + event configuration.
- [[Staff & Permissions|Staff-Accounts-and-Permissions]] — actions are attributed to the acting user.
