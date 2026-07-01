# REST API Reference

The dashboard's web UI is a Vue SPA that talks to a JSON HTTP API served by the embedded Javalin
server. This page documents that API for anyone building integrations.

> The API is internal to the dashboard and may change between versions. Endpoints, payloads and
> responses are derived from `ApiServer.java` for v1.1.0.

## Base & auth

- **Base path:** `/api` (same origin and `port` as the dashboard, default `8095`).
- **Auth header:** `Authorization: Bearer <jwt>` on every `/api/*` request except the public ones
  below. Obtain the token from `POST /api/auth/login`.
- **Content type:** `application/json` for request/response bodies (logo upload is multipart).
- **Permissions:** routes are guarded by the staff [[permissions|Permissions-Reference]]; ADMIN
  bypasses checks. DEMO accounts are blocked from all non-GET requests.

### Public endpoints (no token)

`POST /api/auth/login`, `POST /api/auth/login/totp`, `POST /api/auth/demo`,
`GET /api/auth/demo-available`, `GET /api/branding`, `GET /api/branding/logo`, `GET /health`,
and `GET /api/events/stream` (which authenticates via a `?token=` query param).

### Security headers

Every response carries `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and
`Referrer-Policy: strict-origin-when-cross-origin`. CORS is controlled by
`cors.allowed-origins` (see [[Configuration|Configuration]]).

## Auth & account

| Method | Path | Permission | Purpose |
|---|---|---|---|
| POST | `/api/auth/login` | public | Log in → session token, or a 2FA challenge |
| POST | `/api/auth/login/totp` | public (pre-auth token) | Complete a 2FA challenge |
| GET | `/api/auth/demo-available` | public | Whether the demo account is enabled |
| POST | `/api/auth/demo` | public | One-click read-only demo login |
| POST | `/api/auth/logout` | session | Revoke the current session |
| PUT | `/api/auth/account` | session | Change own username/password |
| GET | `/api/auth/2fa` | session | 2FA status |
| POST | `/api/auth/2fa/setup` | session | Begin TOTP setup (returns secret/QR) |
| POST | `/api/auth/2fa/enable` | session | Confirm & enable 2FA → recovery codes |
| POST | `/api/auth/2fa/disable` | session | Disable 2FA |
| POST | `/api/auth/2fa/recovery-codes` | session | Regenerate recovery codes |
| GET | `/api/auth/sessions` | session | List own sessions |
| DELETE | `/api/auth/sessions/{jti}` | session | Revoke one own session |
| POST | `/api/auth/sessions/revoke-others` | session | Log out everywhere else |
| GET | `/api/admin/sessions` | ADMIN | List all sessions |
| DELETE | `/api/admin/sessions/{jti}` | ADMIN | Revoke any session |

## Server & meta

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/health` | public | Liveness probe |
| GET | `/api/server/overview` | session | Players, TPS, memory, versions, online list |
| GET | `/api/meta/materials` | session | Material list for item pickers |
| GET | `/api/meta/enchantments` | session | Enchantment list (with max levels) for the item editor |
| GET | `/api/analytics/history` | session | Trend samples (`range=1h\|24h\|7d\|30d`) |
| GET | `/api/analytics/activity-heatmap` | session | Login counts by weekday/hour (optional `?uuid=`) |
| GET | `/api/analytics/geo-distribution` | session | Player counts by country (needs EssentialsXGeoIP) |

## Players

| Method | Path | Permission |
|---|---|---|
| GET | `/api/players` | `PLAYERS_VIEW` |
| POST | `/api/players/bulk` | per-action | Bulk action across selected players |
| GET | `/api/players/{uuid}` | `PLAYERS_VIEW` |
| PUT | `/api/players/{uuid}/money` | `PLAYERS_MANAGE` |
| PUT | `/api/players/{uuid}/nickname` | `PLAYERS_MANAGE` |
| GET / PUT / DELETE | `/api/players/{uuid}/homes` (+ `/{name}`) | `PLAYERS_VIEW` / `PLAYERS_MANAGE` |
| GET / POST / DELETE | `/api/players/{uuid}/mail` | `PLAYERS_VIEW` / `PLAYERS_MANAGE` |
| POST / DELETE | `/api/players/{uuid}/mute` | `BANS_MANAGE` |
| POST / DELETE | `/api/players/{uuid}/ban` | `BANS_MANAGE` |
| POST | `/api/players/{uuid}/warn` | `BANS_MANAGE` |
| POST | `/api/players/{uuid}/kick` | `PLAYERS_MANAGE` |
| POST | `/api/players/{uuid}/message` | `PLAYERS_MANAGE` |
| PUT | `/api/players/{uuid}/gamemode` | `PLAYERS_MANAGE` |
| POST | `/api/players/{uuid}/action` | `PLAYERS_MANAGE` |
| POST | `/api/players/{uuid}/give` | `PLAYERS_MANAGE` |
| POST | `/api/players/{uuid}/teleport` | `PLAYERS_MANAGE` |
| GET | `/api/players/{uuid}/inventory` | `INVENTORY_VIEW` |
| PUT / DELETE | `/api/players/{uuid}/inventory/{slot}` | `INVENTORY_MANAGE` |
| GET | `/api/players/{uuid}/geo` | `PLAYERS_VIEW` |
| GET | `/api/players/{uuid}/punishments` | `PLAYERS_VIEW` |
| GET | `/api/players/{uuid}/timeline` | `PLAYERS_VIEW` |
| GET | `/api/players/{uuid}/alts` | `PLAYERS_VIEW` |
| GET / POST / DELETE | `/api/players/{uuid}/notes` (+ `/{id}`) | `PLAYERS_VIEW` / `PLAYERS_MANAGE` |

## Economy

| Method | Path | Permission |
|---|---|---|
| GET | `/api/economy/baltop` | `ECONOMY_VIEW` |
| GET | `/api/economy/stats` | `ECONOMY_VIEW` |
| GET | `/api/economy/insights` | `ECONOMY_VIEW` |
| GET | `/api/economy/transactions` | `ECONOMY_LOG_VIEW` |
| GET | `/api/economy/debts` | `ECONOMY_VIEW` |
| POST | `/api/economy/bulk` | `ECONOMY_MANAGE` |
| POST | `/api/economy/reset-debts` | `ECONOMY_MANAGE` |

## Moderation

| Method | Path | Permission |
|---|---|---|
| GET | `/api/bans` / `/api/bans/mutes` | `BANS_VIEW` |
| GET | `/api/chat` | `CHAT_VIEW` |
| DELETE | `/api/chat/{id}` | `CHAT_MODERATE` |
| GET / PUT | `/api/moderation/templates` | `BANS_MANAGE` (edit: ADMIN) |
| GET / PUT | `/api/moderation/escalation` | `BANS_MANAGE` (edit: ADMIN) |

## Content

| Method | Path | Permission |
|---|---|---|
| GET | `/api/kits` | `KITS_VIEW` |
| PUT / DELETE | `/api/kits/{name}` | `KITS_MANAGE` |
| GET | `/api/warps` | `WARPS_VIEW` |
| PUT / DELETE | `/api/warps/{name}` | `WARPS_MANAGE` |
| POST | `/api/warps/{name}/teleport` | `WARPS_MANAGE` |

## Console & config

| Method | Path | Permission |
|---|---|---|
| POST | `/api/console/execute` | `CONSOLE_EXECUTE` |
| GET | `/api/config` | `CONFIG_VIEW` |
| PUT | `/api/config` | `CONFIG_MANAGE` |

## Server controls

| Method | Path | Permission |
|---|---|---|
| GET / PUT / POST | `/api/admin/whitelist` (+ `DELETE /{name}`) | `SERVER_MANAGE` |
| GET | `/api/admin/worlds` · POST `/api/admin/worlds/{world}` | `SERVER_MANAGE` |
| POST | `/api/admin/save-all` · `/api/admin/stop` | `SERVER_MANAGE` |
| GET / POST | `/api/admin/spawn` | `SERVER_MANAGE` |
| GET / POST | `/api/admin/jails` (+ `DELETE /{name}`, `/jail`, `/unjail`) | `SERVER_MANAGE` |

## Admin tools

| Method | Path | Permission |
|---|---|---|
| POST | `/api/admin/broadcast` | `BROADCAST` |
| POST | `/api/admin/mail-all` | `MAIL_MANAGE` |
| GET | `/api/admin/audit-log` | `AUDIT_LOG` |

## Staff (ADMIN only)

| Method | Path |
|---|---|
| GET / POST | `/api/staff` |
| PUT / DELETE | `/api/staff/{username}` |
| DELETE | `/api/staff/{username}/2fa` |

## Branding

| Method | Path | Permission |
|---|---|---|
| GET | `/api/branding` · `/api/branding/logo` | public |
| PUT | `/api/branding` | ADMIN |
| POST / DELETE | `/api/branding/logo` | ADMIN |

## Modules

| Method | Path | Permission |
|---|---|---|
| GET | `/api/modules` | `MODULES_VIEW` |
| GET / PUT | `/api/modules/chat` | `MODULES_VIEW` / `MODULES_MANAGE` |
| GET / PUT | `/api/modules/protect` | `MODULES_VIEW` / `MODULES_MANAGE` |
| GET / PUT | `/api/modules/discord` | `MODULES_VIEW` / `MODULES_MANAGE` |

## Scheduler

| Method | Path | Permission |
|---|---|---|
| GET | `/api/scheduler/tasks` | `SCHEDULER_VIEW` |
| POST | `/api/scheduler/tasks` | `SCHEDULER_MANAGE` |
| PUT / DELETE | `/api/scheduler/tasks/{id}` | `SCHEDULER_MANAGE` |
| POST | `/api/scheduler/tasks/{id}/toggle` · `/run` | `SCHEDULER_MANAGE` |

## Live events (SSE)

```
GET /api/events/stream?token=<jwt>
```

Server-Sent Events stream (EventSource can't set headers, so the token goes in the query string and
must belong to an active session). On connect the server sends a `connected` handshake; thereafter you
receive live events such as `console-line`, `chat-line`, `player-join` and `player-leave`. Used by the
[[Live Console|Live-Console]], [[Chat Moderation|Chat-Moderation]] and
[[Overview|Dashboard-Overview-and-Analytics]] pages.

## Errors

- `401` — missing/invalid/revoked token (the SPA redirects to login).
- `403` — authenticated but lacking the required permission, or a DEMO account attempting a write.
- `429` — login rate limit exceeded (5 failures/IP/minute).
- `500` — `{ "error": "..." }` for EssentialsX or unexpected errors.
