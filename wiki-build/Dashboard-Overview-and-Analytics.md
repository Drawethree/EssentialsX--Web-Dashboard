# Dashboard & Analytics

## Overview (home page)

The landing page (`/`) is a live snapshot of your server. It's available to every logged-in user.

- **Stat cards with sparklines** — online players, TPS, memory usage, and total known players, each
  with a mini trend chart.
- **Online players list** — click any player to jump to their [[profile|Players]].
- **Server info** — Bukkit/Paper version, EssentialsX version, uptime, currency symbol.
- **Recent activity** — the last few [[audit log|Audit-Log]] entries.
- **Quick actions** — shortcuts to Players, Console, Economy and Broadcast.
- **Live updates** — player join/leave events arrive in real time over the SSE stream; overview data
  also refreshes on a short poll.

## Analytics

The Analytics page (`/analytics`) shows **historical trends** sampled by the server over time.

- **Time ranges:** 1h, 24h, 7d.
- **Metrics:** online player count, TPS, memory used, and total economy (money supply).
- Each chart shows current / average / peak values.

Data is collected periodically by the built-in metrics sampler and stored in the dashboard's SQLite
database, so trends start filling in after the plugin has been running for a while. If you've just
installed it, expect a brief "no data yet" period.

## Related

- [[Players|Players]] — drill into any online player from the overview.
- [[Live Console|Live-Console]] — watch server output live.
- [[Economy|Economy]] — money-supply trends in depth.
