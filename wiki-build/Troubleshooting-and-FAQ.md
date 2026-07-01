# Troubleshooting & FAQ

## I can't reach the dashboard

- **Check it started.** Run `/essdashboard` in-game (or look in the console) for the printed URL and
  status. If EssentialsX isn't installed, the plugin disables itself.
- **Port not open.** The panel listens on `port` (default `8095`). Allow/forward that TCP port in your
  firewall and hosting panel. Test locally first: `http://localhost:8095`.
- **Wrong address.** Use your server's real IP/hostname. Set `host` in
  [[Configuration|Configuration]] so the startup URL prints correctly (cosmetic only).

## "Port already in use" / bind error

Another service is using the port. Change `port` in `config.yml` and restart, or free the port.

## The page loads but API calls fail (CORS / blocked)

If you locked `cors.allowed-origins` to a domain, the browser must load the panel from **exactly** that
origin (scheme + host + port). For LAN/IP access use `*`. See [[Configuration|Configuration]].

## I forgot the admin password

The admin account lives in the dashboard's SQLite database, not the YAML. To recover:

1. Stop the server.
2. Set a fresh `superuser.username` / `superuser.password` in `config.yml`. The bootstrap only creates
   the account if it doesn't already exist, so to reset an existing one, remove that user row from the
   `users` table in `plugins/EssDashboard/dashboard.db` (any SQLite browser), then restart — the
   account is recreated from config and you'll be asked to change the password on first login.

## I'm locked out of 2FA

- Use one of your **recovery codes** at the login 2FA prompt.
- Otherwise, another **admin** can reset your 2FA from
  [[Staff & Permissions|Staff-Accounts-and-Permissions]].
- If the *only* admin is locked out, clear that user's `totp_enabled`/`totp_secret` in the `users`
  table of `dashboard.db` and restart.

## A module panel says "not installed"

The dashboard only shows panels for add-ons that are actually present. Install the matching plugin and
restart:

| Feature missing | Install |
|---|---|
| GeoIP location on player profiles | EssentialsXGeoIP |
| Spawn get/set on Server Controls | EssentialsXSpawn |
| Chat format editor | EssentialsXChat |
| Protect settings editor | EssentialsXProtect |
| Discord config editor | EssentialsXDiscord |

See [[EssentialsX Modules|EssentialsX-Modules]].

## Economy shows nothing / wrong currency

EssentialsX economy must be active (and Vault is recommended as a bridge). Confirm balances work
in-game first. See [[Economy|Economy]].

## Console won't run commands

- The user needs `CONSOLE_EXECUTE` (see [[Permissions Reference|Permissions-Reference]]).
- `console.allow-commands` may be `false` (read-only mode).
- The command may be in `console.blocked-commands` (`stop`, `restart`, `op`, `deop` by default;
  `namespace:` prefixes like `minecraft:stop` are matched too). Blocked attempts are still audited.
  See [[Live Console|Live-Console]].

## Live console connects but shows no output

The status reads "Connected" but no log lines stream in:

- **Check the startup log.** On enable, the plugin logs `Live console attached…` with the captured
  log level. If you instead see a warning that it couldn't attach, the live feed will stay empty.
- **Reverse proxy buffering.** The console uses Server-Sent Events (SSE). Some proxies buffer or
  close streaming responses — for nginx, set `proxy_buffering off;` and a long `proxy_read_timeout`
  on the dashboard location, and don't gzip the event stream.
- **Content-Security-Policy.** If you front the panel with your own CSP, make sure `connect-src`
  allows the dashboard origin so the browser can open the `/api/events/stream` connection.

## "No SLF4J provider" warnings

Harmless — the plugin bundles a simple SLF4J binding so Javalin logs cleanly. If you still see a
warning it won't affect functionality.

## Running it safely on the public internet

Put the dashboard behind a reverse proxy that terminates TLS (nginx, Caddy, Traefik) and forwards to
`127.0.0.1:8095`, lock `cors.allowed-origins` to your panel domain, change the admin password, and
enable [[2FA|First-Login-and-Security]]. Don't expose plain HTTP. Full copy-paste configs are in
**[[Reverse Proxy & HTTPS|Reverse-Proxy-and-HTTPS]]**; see also the hardening section of
[[Configuration|Configuration]].

## Where is my data stored?

In `plugins/EssDashboard/`:

| File | Contents |
|---|---|
| `config.yml` | Settings (incl. generated `jwt.secret`) |
| `dashboard.db` | SQLite DB: staff accounts, sessions, player index, punishments, notes, login history, economy & chat logs, metrics, scheduled tasks, branding/moderation settings |
| `audit.log` (+ `.1`/`.2`/`.3`) | Rotating audit trail |

For backups, migration, `jwt.secret` rotation and password recovery, see
[[Data, Backups & Recovery|Data-Backups-and-Recovery]].

## Still stuck?

Open an issue on the [GitHub repository](https://github.com/Drawethree/EssentialsX--Web-Dashboard).
