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

## Opening the port

The dashboard listens on `port` (default **8095**) from `config.yml`. To reach it from outside the
host you must allow/forward that TCP port in your firewall and (if applicable) your hosting panel.
For a public deployment, put it behind HTTPS — see [[Configuration|Configuration]] and
[[Troubleshooting & FAQ|Troubleshooting-and-FAQ]].

## In-game command

| Command | Aliases | Permission | Purpose |
|---|---|---|---|
| `/essdashboard` | `/essdash`, `/edash` | `essdashboard.admin` (default: op) | Print the dashboard URL and status |

## Next steps

- [[Configuration|Configuration]] — every `config.yml` option.
- [[First Login & Security|First-Login-and-Security]] — passwords, 2FA, sessions.
- [[Staff & Permissions|Staff-Accounts-and-Permissions]] — add limited-access staff accounts.
