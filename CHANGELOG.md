# EssentialsX Dashboard — Changelog

## 1.1.0 — Security & Automation Update

Three flagship additions focused on account security and hands-off server management.

**Two-Factor Authentication (2FA)**
- Protect staff logins with time-based one-time codes from any authenticator app (Google
  Authenticator, Authy, 1Password, and others). Enrol with a scannable QR code right in the panel.
- Get **10 one-time recovery codes** when you turn it on — download or copy them in case you lose
  your device.
- Admins can reset 2FA for a locked-out team member from the Staff page.

**Active Sessions & "Log out everywhere"**
- See every device currently signed in to your account — browser, OS, IP, and last activity — on
  the new **Security** page.
- Revoke any individual session, or sign out of all *other* devices in one click.
- Admins get a server-wide view of every active session and can revoke any of them.
- Changing your password (or having an admin reset it) now signs you out everywhere automatically.

**Scheduled Tasks**
- A new **Scheduler** page to automate your server: timed **broadcasts**, **console commands**,
  **mail-to-everyone**, and **timed restarts** with an on-screen countdown warning to players.
- Run tasks **once** at a set time or **repeat** them on any interval (minutes, hours, or days).
- Pause/resume, edit, delete, or "run now" any task, and see each one's last result.

**White-Label Branding**
- Make the panel yours from the new **Branding** page: set your own **panel name**, **accent
  colour** (with live preview and handy presets), and upload a **custom logo**.
- Your branding shows everywhere — sidebar, login screen, and browser tab.

**Polished Audit Log**
- Redesigned as a clean, scannable table — **Time · User · Action · Details** — instead of raw text.
- **Search** any user, action, or detail, and **filter by action type** from a dropdown.
- Every action now shows a **colour-coded badge** (red for destructive/security events, amber for
  config/access changes, green for sign-ins and reversals) and a readable label.
- Friendly relative timestamps ("2m ago") with the exact time on hover, a proper empty state, and a
  **CSV export** with real Time/User/Action/Details columns.

## 1.0.0 — Initial Release

The first release of **EssentialsX Dashboard** — a web panel that lets you run your
EssentialsX server from any browser.

**Highlights**
- **Players (online & offline):** search every player, then edit their balance, nickname,
  homes, and mail — even when they're offline. Mute, ban, kick, message, and change gamemode
  for online players.
- **Economy Command Center:** live baltop, total/average wealth stats, and bulk give/take to
  all or only online players.
- **Live Web Console:** watch real-time server output and chat in your browser, and run any
  server command — with full command history.
- **Kits Editor:** create, edit, and delete EssentialsX kits visually; changes reload instantly.
- **Warps:** add, edit, delete, and teleport players to warp points.
- **Config Editor:** edit the EssentialsX `config.yml` with YAML validation; saving hot-reloads
  Essentials.
- **Bans & Mutes overview:** see every active ban and mute at a glance.
- **Tools:** server-wide broadcasts and mail-to-everyone.
- **Staff accounts & permissions:** create logins with granular, per-feature permissions, plus
  an optional read-only demo account.
- **Security:** JWT login with bcrypt password hashing, login rate-limiting, configurable CORS,
  and a full audit log of every action taken through the dashboard.

Set it up by dropping the jar in `plugins/`, then open `http://your-server-ip:8095` and log in
with the default `admin` account (change the password in `config.yml` first!).
