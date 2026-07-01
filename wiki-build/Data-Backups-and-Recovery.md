# Data, Backups & Recovery

Everything the dashboard stores lives in **`plugins/EssDashboard/`**. This page explains what each file
is, how to back it up, and how to recover from common lockouts.

## Where your data lives

| File | Contents | Sensitive? |
|---|---|---|
| `config.yml` | All settings, including the auto-generated **`jwt.secret`** | 🔴 Yes — holds the session-signing secret |
| `dashboard.db` | SQLite database: staff accounts (bcrypt-hashed passwords, 2FA secrets), sessions, player index, punishments, staff notes, login history, economy & chat logs, metrics samples, scheduled tasks, branding & moderation settings | 🔴 Yes — account & session data |
| `audit.log` (+ `.1`, `.2`, `.3`) | Rotating [[audit trail|Audit-Log]] (5 MB per file, 3 backups) | 🟠 Contains usernames, IPs, actions |

> The GeoIP database (`.mmdb`) is **not** stored here — it's read from EssentialsXGeoIP at runtime.

## Backups

Because it's just files, backing up is straightforward:

1. **Stop the server** (or at least ensure the dashboard is idle) for a consistent SQLite snapshot.
2. Copy the whole **`plugins/EssDashboard/`** folder somewhere safe.
3. Store backups securely — `config.yml` and `dashboard.db` are sensitive (see table above).

To restore, stop the server, drop the folder back in place, and start up again.

> **Tip:** keeping `config.yml` in your backups preserves your `jwt.secret`, so existing sessions
> remain valid after a restore. If you *rotate* the secret (below), everyone is logged out.

## Rotating the `jwt.secret`

The `jwt.secret` signs every session token. If it may have leaked (e.g. someone read your
`config.yml`), rotate it:

1. Stop the server.
2. In `config.yml`, blank it out: `jwt.secret: ""`.
3. Start the server — a fresh random secret is generated and saved.

All existing sessions are immediately invalidated and everyone must log in again.

## Recovering a lost admin password

The admin account lives in `dashboard.db`, **not** in `config.yml`. The bootstrap in `config.yml` only
creates the superuser **if it doesn't already exist**, so to reset an existing admin:

1. Stop the server.
2. Open `plugins/EssDashboard/dashboard.db` in any SQLite browser
   (e.g. [DB Browser for SQLite](https://sqlitebrowser.org/)).
3. Delete the admin's row from the **`users`** table.
4. Make sure `superuser.username` / `superuser.password` in `config.yml` are set to what you want.
5. Start the server — the account is recreated from config, and you'll be forced to set a new password
   on first login.

## Recovering from a 2FA lockout

- **You have recovery codes:** enter one at the login 2FA prompt instead of a TOTP code.
- **Another admin is available:** they can reset your 2FA from
  [[Staff & Permissions|Staff-Accounts-and-Permissions]].
- **The only admin is locked out:** stop the server, and in the `users` table of `dashboard.db` clear
  that user's `totp_enabled` / `totp_secret` columns, then restart.

## Migrating to another server

Move the entire `plugins/EssDashboard/` folder to the new host. Everything — accounts, history,
branding, scheduled tasks — comes along. Update `config.yml` (`host`, `server-address`, CORS) for the
new address if needed.

## Related

- [[Configuration|Configuration]] — every setting in `config.yml`.
- [[First Login & Security|First-Login-and-Security]] — passwords, 2FA, sessions.
- [[Audit Log|Audit-Log]] — the `audit.log` files and rotation.
- [[Troubleshooting & FAQ|Troubleshooting-and-FAQ]] — more recovery scenarios.
