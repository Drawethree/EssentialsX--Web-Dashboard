<div align="center">

<img src="src/frontend/src/assets/logo.png" alt="EssentialsX Dashboard" width="96" height="96" />

# EssentialsX Dashboard

**A modern web control panel for [EssentialsX](https://essentialsx.net/).**
Manage your server's economy, players (online *and* offline), kits, warps, bans, a live console, and
EssentialsX modules from any browser — no in-game commands required.

[![Build](https://github.com/Drawethree/EssentialsX--Web-Dashboard/actions/workflows/build.yml/badge.svg)](https://github.com/Drawethree/EssentialsX--Web-Dashboard/actions/workflows/build.yml)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/Drawethree/EssentialsX--Web-Dashboard?include_prereleases&sort=semver)](https://github.com/Drawethree/EssentialsX--Web-Dashboard/releases)
[![Wiki](https://img.shields.io/badge/docs-wiki-brightgreen.svg)](https://github.com/Drawethree/EssentialsX--Web-Dashboard/wiki)

[**📖 Documentation**](https://github.com/Drawethree/EssentialsX--Web-Dashboard/wiki) ·
[**🚀 Live Demo**](http://213.170.135.173:8026/) ·
[**⬇️ Download**](https://github.com/Drawethree/EssentialsX--Web-Dashboard/releases) ·
[**🐛 Report a bug**](https://github.com/Drawethree/EssentialsX--Web-Dashboard/issues/new/choose)

</div>

---

The dashboard ships as a **single Paper/Spigot plugin** (`EssDashboard.jar`) with an embedded web
server, so there's nothing external to host: drop in the jar, open a port, and log in.

> 🚀 **Try the live demo:** **http://213.170.135.173:8026/** — explore the panel with the read-only
> demo account (one-click **Demo** login on the sign-in page).

## ✨ Features

- **Players** — search every known player; edit balance, nickname, homes, mail, mutes and bans even
  while they're **offline**. Heal / feed / fly / god / give / teleport / gamemode for online players.
- **Inventory & ender chest editor** — view and edit items with full detail (enchantments, lore,
  durability, custom names).
- **Economy** — paginated baltop, total/average wealth, supply trends, transaction ledger, bulk
  give/take, and debt cleanup.
- **Moderation** — bans, mutes, warnings with auto-escalation, punishment templates, and live chat
  moderation with searchable history.
- **Live Console** — real-time server output + chat, run commands straight from the browser.
- **Server Controls** — whitelist, world time/weather, save/stop, spawn and jails.
- **EssentialsX Modules** — auto-detect add-ons; edit Chat formats, Protect settings and the Discord
  config; show player GeoIP location.
- **Scheduler** — timed broadcasts, commands, mail-all and restarts (one-off or recurring).
- **Analytics** — historical online/TPS/memory/economy charts, a login activity heatmap, and a
  player geography breakdown.
- **Security** — staff accounts with granular permissions, full audit log, JWT auth, **2FA/TOTP**,
  session management, login rate-limiting, and an optional read-only demo mode.
- **White-label branding** — set your server name, accent color, and logo; dark/light themes.

## ✅ Requirements

| Requirement | Notes |
|---|---|
| Server | Paper or Spigot **1.17+** (the embedded web server needs Java 17) |
| Java | **17+** |
| EssentialsX | **2.19+** recommended (`net.essentialsx`) — hard dependency |
| Optional | Vault (economy bridge), EssentialsXSpawn, EssentialsXChat, EssentialsXProtect, EssentialsXGeoIP, EssentialsXDiscord |

## 🚀 Quick start

1. Download the latest `EssentialsX-Dashboard-x.y.z.jar` from
   [**Releases**](https://github.com/Drawethree/EssentialsX--Web-Dashboard/releases) and drop it into
   your `plugins/` folder (make sure EssentialsX is installed too), then start the server.
2. Open `http://<your-server-ip>:8095` and log in with the bootstrapped admin account
   (`admin` / `changeme`).
3. **Change the default password immediately** (you'll be prompted on first login), then review
   `plugins/EssDashboard/config.yml`.

Full walkthrough: **[Installation](https://github.com/Drawethree/EssentialsX--Web-Dashboard/wiki/Installation)**.

## 🛠️ Building from source

You need **JDK 17** and **Maven** (Node/npm are downloaded automatically by the build):

```bash
git clone https://github.com/Drawethree/EssentialsX--Web-Dashboard.git
cd EssentialsX--Web-Dashboard
mvn clean package
# → target/EssentialsX-Dashboard-<version>.jar
```

See [Building from Source](https://github.com/Drawethree/EssentialsX--Web-Dashboard/wiki/Building-from-Source)
and [CONTRIBUTING.md](CONTRIBUTING.md) for the frontend dev workflow and project layout.

## 🔒 Security

- Passwords are bcrypt-hashed; sessions use signed, revocable JWTs. Login and the 2FA step are both
  rate-limited. The dashboard ships secure response headers (CSP, HSTS, `nosniff`,
  `X-Frame-Options`, `Referrer-Policy`).
- **The dashboard speaks plain HTTP and expects TLS from a reverse proxy.** For any public
  deployment, follow the
  [Reverse Proxy & HTTPS](https://github.com/Drawethree/EssentialsX--Web-Dashboard/wiki/Reverse-Proxy-and-HTTPS)
  and
  [First Login & Security](https://github.com/Drawethree/EssentialsX--Web-Dashboard/wiki/First-Login-and-Security)
  guides.
- Found a vulnerability? Please report it privately — see [SECURITY.md](SECURITY.md).

## 📖 Documentation

Everything lives in the **[Wiki](https://github.com/Drawethree/EssentialsX--Web-Dashboard/wiki)**:
installation, configuration reference, every feature, the REST API, permissions, and troubleshooting.

## 🤝 Contributing

Contributions are welcome! Read [CONTRIBUTING.md](CONTRIBUTING.md) for the dev setup, build steps,
and PR guidelines. Bugs and ideas go through the
[issue tracker](https://github.com/Drawethree/EssentialsX--Web-Dashboard/issues/new/choose).

## 📜 License

Licensed under the **[GNU General Public License v3.0](LICENSE)**. You're free to use, study, share,
and modify it; derivative works that you distribute must also be released under the GPL-3.0.

EssentialsX Dashboard is an independent project and is not affiliated with or endorsed by the
EssentialsX team.
