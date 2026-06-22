# EssentialsX Dashboard

A modern **web control panel for EssentialsX** — manage your server's economy, players
(online *and* offline), kits, warps, bans, a live console, and EssentialsX modules from any
browser.

## Requirements

| Requirement | Notes |
|---|---|
| Server | Paper or Spigot **1.17+** (the embedded web server needs Java 17) |
| EssentialsX | **2.19+** recommended (`net.essentialsx`) |
| Optional | Vault (economy bridge), EssentialsXSpawn, EssentialsXChat, EssentialsXProtect, EssentialsXGeoIP, EssentialsXDiscord |

## Setup

1. Drop `EssDashboard.jar` into your `plugins/` folder and start the server.
2. Open `http://<your-server-ip>:8095` and log in with the bootstrapped admin account
   (`admin` / `changeme`).
3. **Change the default password immediately** (user menu → Change Credentials) and review
   `plugins/EssDashboard/config.yml`.

## Configuration (`config.yml`)

- `port` — web server port (default `8095`).
- `host` — public hostname/IP used in the printed URL.
- `superuser` — the auto-created admin account.
- `jwt.secret` — generated automatically on first run; keep it secret.
- `cors.allowed-origins` — `*` for LAN/IP access, or lock to your panel domain.
- `demo` — optional read-only showcase account.
- `console.allow-commands` — allow running server commands from the web console.

## Features

- **Players** — search every player; edit balance, nickname, homes, mail, mutes and bans even
  while they're **offline**. Heal/feed/fly/god/give/teleport and gamemode for online players.
- **Inventory & ender chest** — view and edit an online player's items with full detail
  (enchantments, lore, durability, custom names).
- **Economy** — paginated baltop, total/average wealth, and bulk give/take.
- **Bans & Mutes**, **Kits** (visual editor with item icons), **Warps**.
- **Live Console** — real-time server output + chat, run commands from the browser.
- **Server Controls** — whitelist, world time/weather, save/stop, spawn and jails.
- **Modules** — auto-detect EssentialsX add-ons; edit Chat formats, Protect settings, and the
  Discord config; show player GeoIP location.
- **Staff accounts** with granular permissions, full **audit log**, JWT auth, login
  rate-limiting and an optional demo mode.

## Security notes

- Passwords are bcrypt-hashed; sessions use signed, revocable JWTs. Login and the 2FA
  step are both rate-limited.
- The dashboard ships secure response headers (CSP, HSTS, `nosniff`, `X-Frame-Options`,
  `Referrer-Policy`).

### Production deployment checklist

- **Terminate TLS in front of the panel.** Run it behind a reverse proxy (nginx, Caddy,
  Traefik…) that handles HTTPS; the dashboard speaks plain HTTP and assumes a TLS proxy.
  HSTS only takes effect once you're on HTTPS.
- **Don't log query strings at the proxy.** The live-console SSE stream authenticates with
  a `?token=` query parameter (the browser `EventSource` API can't send headers). Make sure
  your proxy/access logs strip or omit query strings so session tokens don't land in logs.
- **Change the default admin password** before exposing the panel (user menu → Change
  Credentials), and enable 2FA for admin accounts.
- **Restrict access.** Set `cors.allowed-origins` to your panel's origin for a public
  deployment, and use `security.allowed-ips` to limit who can connect where possible.
- **Protect `config.yml`.** It holds the generated `jwt.secret`; anyone who can read it can
  forge sessions. Lock it down with filesystem permissions (e.g. `chmod 600`).
- **Console access is powerful.** `console.allow-commands` lets `CONSOLE_EXECUTE` staff run
  server commands; keep the `console.blocked-commands` list current and grant the permission
  sparingly. Scheduled command tasks honour the same blocklist.
- **Keep the demo account off in production.** `demo.enabled` is `false` by default; only
  turn it on for a public, read-only showcase.
