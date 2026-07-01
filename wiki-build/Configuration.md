# Configuration

All settings live in `plugins/EssDashboard/config.yml`, created on first start. Edit it and restart the
server (or reload the plugin) to apply changes.

## Full reference

| Key | Default | Description |
|---|---|---|
| `port` | `8095` | TCP port the embedded web server listens on. Open/forward it to reach the panel remotely. |
| `host` | `""` | Public hostname/IP used only when printing the dashboard URL on startup. Blank prints `<your-server-ip>`. |
| `server-address` | `""` | Public address players use to connect to your Minecraft server, shown on the Overview. Blank auto-detects from the server's bind IP/port. |
| `superuser.username` | `admin` | The admin account auto-created on first start. |
| `superuser.password` | `changeme` | Initial admin password. **Change this** before launch, or you'll be forced to change it on first login. |
| `jwt.secret` | `""` | Leave blank — a strong random secret is generated and saved here on first run. Keep it private. |
| `jwt.expiry-hours` | `24` | How long a login session stays valid, in hours. |
| `cors.allowed-origins` | `"*"` | Allowed browser origins. `*` is fine for LAN/IP access. Lock to a domain for production, e.g. `https://panel.myserver.com`. |
| `security.allowed-ips` | `[]` | IP allowlist for reaching the panel. Empty = allow everyone. Otherwise only the listed IPv4/IPv6 addresses or CIDR ranges may connect; everything else gets `403` and an `IP_BLOCKED` audit entry. Behind a reverse proxy the source IP is the proxy's — allowlist at the proxy instead. |
| `demo.enabled` | `false` | Enable an optional **read-only** demo account (great for screenshots/showcasing). |
| `demo.username` | `demo` | Demo account username. |
| `demo.password` | `demo` | Demo account password. |
| `console.allow-commands` | `true` | Allow running server commands from the web console. Set `false` to make it read-only. |
| `console.blocked-commands` | `[stop, restart, op, deop]` | Commands that may never run from the web console (matched on the first word, case-insensitive, with any `namespace:` prefix stripped — so `minecraft:stop` matches `stop`). Also enforced on scheduled command tasks. Blocked attempts are still audited. |
| `notifications.webhook-url` | `""` | Discord webhook URL for staff/server action notifications. Blank disables them. |
| `notifications.events` | `[BAN, UNBAN, KICK, MUTE, LOGIN_FAIL, SERVER_STOP]` | Which audited actions trigger a Discord notification. |

## Default config.yml

```yaml
port: 8095
host: ""
server-address: ""

superuser:
  username: admin
  password: changeme

jwt:
  secret: ""
  expiry-hours: 24

cors:
  allowed-origins: "*"

security:
  allowed-ips: []

demo:
  enabled: false
  username: demo
  password: demo

console:
  allow-commands: true
  blocked-commands:
    - stop
    - restart
    - op
    - deop

notifications:
  webhook-url: ""
  events:
    - BAN
    - UNBAN
    - KICK
    - MUTE
    - LOGIN_FAIL
    - SERVER_STOP
```

## Production hardening

If the panel is reachable from the internet:

1. **Change the admin password** and enable [[2FA|First-Login-and-Security]].
2. **Lock CORS** — set `cors.allowed-origins` to your exact panel domain, not `*`.
3. **Front it with HTTPS** — run the dashboard behind a reverse proxy (nginx, Caddy, Traefik) that
   terminates TLS and forwards to `127.0.0.1:8095`. Do not expose plain HTTP publicly. (HSTS is sent
   automatically but only takes effect over HTTPS.) Copy-paste configs:
   **[[Reverse Proxy & HTTPS|Reverse-Proxy-and-HTTPS]]**.
4. **Restrict the port / source IPs** — only expose `port` to trusted networks where possible, and/or
   use `security.allowed-ips` (or the proxy) to allowlist who can connect.
5. **Keep `jwt.secret` private** — it signs every session token. Treat the config like a password
   (e.g. `chmod 600 config.yml`).
6. **Review `console.blocked-commands`** and consider `console.allow-commands: false` for non-trusted
   staff.
7. **Don't log query strings at the proxy** — the live-console stream authenticates with a `?token=`
   query parameter (the browser cannot send headers for it), so keep it out of access logs.

See also: [[Reverse Proxy & HTTPS|Reverse-Proxy-and-HTTPS]] for full nginx/Caddy/Apache configs,
[[Data, Backups & Recovery|Data-Backups-and-Recovery]] for backups and password recovery, and
[[Troubleshooting & FAQ|Troubleshooting-and-FAQ]] for common issues.
