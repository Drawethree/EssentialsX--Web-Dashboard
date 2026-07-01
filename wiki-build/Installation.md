# Installation

EssentialsX Dashboard is a normal Bukkit/Paper plugin with an **embedded web server** — there is no
separate web app to deploy.

## Requirements

| Requirement | Notes |
|---|---|
| Server | Paper or Spigot **1.17+** |
| Java | **17 or newer** (the embedded web server requires it) |
| EssentialsX | **2.19+** recommended — this is a **hard dependency** (`depend: Essentials`) |
| Optional add-ons | EssentialsXSpawn, EssentialsXChat, Vault *(soft-depends)*; EssentialsXProtect, EssentialsXGeoIP, EssentialsXDiscord are auto-detected at runtime |

If EssentialsX is not installed, the plugin disables itself on startup.

## Steps

1. **Install EssentialsX** (and any optional add-ons you want to manage) and confirm the server boots.
2. **Drop the jar** — copy `EssentialsX-Dashboard-1.1.0.jar` (built as `EssDashboard`) into `plugins/`.
   See [[Building from Source|Building-from-Source]] if you need to compile it.
3. **Start the server.** On first run the plugin:
   - creates `plugins/EssDashboard/config.yml`,
   - generates a strong random `jwt.secret`,
   - creates its SQLite database `plugins/EssDashboard/dashboard.db`,
   - bootstraps the `admin` superuser account,
   - prints the dashboard URL to the console.
4. **Open the dashboard** at `http://<your-server-ip>:8095` (default port `8095`).
5. **Log in** with the default credentials:
   - Username: `admin`
   - Password: `changeme`
6. You'll be **forced to change the password** on first login. Do it. See
   [[First Login & Security|First-Login-and-Security]].

> 📸 **Screenshot:** _The sign-in screen and the forced "change password" prompt on first login._
> _(See [how to add screenshots](Images).)_

## Verify it loaded

On a successful start you'll see lines like these in the console — a quick way to confirm everything
came up:

```text
[EssDashboard] Enabling EssDashboard v1.1.0
[EssDashboard] Hooked into EssentialsX 2.20.1
[EssDashboard] Live console attached (level: INFO)
[EssDashboard] Dashboard running at http://<your-server-ip>:8095
```

Or run **`/essdashboard`** (aliases `/essdash`, `/edash`) in-game at any time to print the URL and
status. If EssentialsX isn't installed, the plugin disables itself — install it first.

## Opening the port

The dashboard listens on `port` (default **8095**) from `config.yml`. To reach it from outside the
host you must allow/forward that TCP port. First confirm it works locally with
`http://localhost:8095`, then:

- **Self-hosted / VPS:** allow the TCP port in your OS firewall (e.g. `ufw allow 8095/tcp`) and, if
  behind a home router, forward it. Prefer **not** exposing the raw port publicly — front it with
  HTTPS instead (see below).
- **Hosting panel (Pterodactyl, Multicraft, etc.):** the port must be one of your server's
  **allocations**. Add/assign an extra port in the panel, then set `port` in `config.yml` to that
  allocated port (many hosts only route ports they've allocated to you). Ask your host if unsure.
- **Docker:** publish the port, e.g. `-p 8095:8095`.

For any **public** deployment, don't expose plain HTTP — put the dashboard behind a reverse proxy that
terminates TLS. See **[[Reverse Proxy & HTTPS|Reverse-Proxy-and-HTTPS]]**.

## In-game command

| Command | Aliases | Permission | Purpose |
|---|---|---|---|
| `/essdashboard` | `/essdash`, `/edash` | `essdashboard.admin` (default: op) | Print the dashboard URL and status |

## Next steps

- [[Configuration|Configuration]] — every `config.yml` option.
- [[First Login & Security|First-Login-and-Security]] — passwords, 2FA, sessions.
- [[Reverse Proxy & HTTPS|Reverse-Proxy-and-HTTPS]] — put the panel behind TLS for public access.
- [[Staff & Permissions|Staff-Accounts-and-Permissions]] — add limited-access staff accounts.
- [[Data, Backups & Recovery|Data-Backups-and-Recovery]] — back up your data and recover lockouts.
