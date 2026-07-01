# EssentialsX Dashboard

A modern **web control panel for [EssentialsX](https://essentialsx.net/)**. Manage your server's
economy, players (online *and* offline), kits, warps, bans, a live console, and EssentialsX modules
from any browser — no in-game commands required.

The dashboard ships as a single Paper/Spigot plugin (`EssDashboard.jar`) with an embedded web server,
so there's nothing external to host: drop in the jar, open a port, and log in.

> 🚀 **Live demo:** **http://213.170.135.173:8026/** — explore the panel with the read-only demo
> account (one-click **Demo** login on the sign-in page).

---

## Highlights

- **Players** — search every known player; edit balance, nickname, homes, mail, mutes and bans even
  while they're **offline**. Heal / feed / fly / god / give / teleport / gamemode for online players.
- **Inventory & ender chest editor** — view and edit items with full detail (enchantments, lore,
  durability, custom names).
- **Economy** — paginated baltop, total/average wealth, supply trends, transaction ledger, bulk
  give/take.
- **Moderation** — bans, mutes, warnings with auto-escalation, punishment templates, and live chat
  moderation with searchable history.
- **Live Console** — real-time server output + chat, run commands straight from the browser.
- **Server Controls** — whitelist, world time/weather, save/stop, spawn and jails.
- **Modules** — auto-detect EssentialsX add-ons; edit Chat formats, Protect settings and the Discord
  config; show player GeoIP location.
- **Scheduler** — timed broadcasts, commands, mail-all and restarts (one-off or recurring).
- **Security** — staff accounts with granular permissions, full audit log, JWT auth, 2FA/TOTP, login
  rate-limiting and an optional read-only demo mode.
- **White-label branding** — set your server name, accent color, and logo; dark/light themes.

## Requirements

| Requirement | Notes |
|---|---|
| Server | Paper or Spigot **1.17+** (the embedded web server needs Java 17) |
| Java | **17+** |
| EssentialsX | **2.19+** recommended (`net.essentialsx`) |
| Optional | Vault (economy bridge), EssentialsXSpawn, EssentialsXChat, EssentialsXProtect, EssentialsXGeoIP, EssentialsXDiscord |

## Quick start

1. Drop `EssDashboard.jar` into your `plugins/` folder and start the server.
2. Open `http://<your-server-ip>:8095` and log in with the bootstrapped admin account
   (`admin` / `changeme`).
3. **Change the default password immediately**, then review `plugins/EssDashboard/config.yml`.

Full steps: **[[Installation|Installation]]**.

## Where to next

| If you want to… | Go to |
|---|---|
| Install and run the plugin | [[Installation|Installation]] |
| Build the jar yourself | [[Building from Source|Building-from-Source]] |
| Tune ports, CORS, demo mode, webhooks | [[Configuration|Configuration]] |
| Secure the panel (password, 2FA, sessions) | [[First Login & Security|First-Login-and-Security]] |
| Put it behind HTTPS for public access | [[Reverse Proxy & HTTPS|Reverse-Proxy-and-HTTPS]] |
| Add staff with limited access | [[Staff & Permissions|Staff-Accounts-and-Permissions]] |
| Back up data / recover a lockout | [[Data, Backups & Recovery|Data-Backups-and-Recovery]] |
| Explore a specific feature | See the **Features** section in the sidebar |
| Integrate with the HTTP API | [[REST API Reference|REST-API-Reference]] |
| Fix a problem | [[Troubleshooting & FAQ|Troubleshooting-and-FAQ]] |
