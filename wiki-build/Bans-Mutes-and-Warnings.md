# Bans, Mutes & Warnings

Moderation tools backed by Bukkit's ban list and EssentialsX mutes, plus the dashboard's own warning
and escalation system.

**Permissions:** `BANS_VIEW` to view lists, `BANS_MANAGE` to ban/mute/warn and edit templates.

## Bans & mutes list (`/bans`)

- Paginated lists of **active bans** (from the Bukkit ban list) and **active mutes** (from
  EssentialsX).
- Each entry shows the reason and expiry (permanent when there's no expiry).
- **CSV export** for bans.
- Each row links straight to the player's [[profile|Players]] to manage the punishment. (Name bans
  that can't be resolved to a known player — e.g. IP bans — have no link.)

## Issuing punishments

Punish from a player's [[profile|Players]] **Actions** tab, or inline from the
[[Chat Moderation|Chat-Moderation]] feed:

- **Ban / Unban** — with reason and optional duration (temporary or permanent).
- **Mute / Unmute** — with optional duration.
- **Kick** — with reason (online players).
- **Warn** — records a warning that can **auto-escalate** (see below).

All actions are audited and can be forwarded to Discord (see [[Audit Log|Audit-Log]]).

## Warnings & auto-escalation

Warnings are tracked per player. With **escalation rules**, hitting a warning threshold automatically
triggers a punishment — e.g. *3 warnings → 1-hour mute*, *5 warnings → ban*.

## Moderation settings (`/moderation`, admin only)

Admins configure two things here:

- **Punishment templates** — reusable presets (label, type, reason, duration) so staff apply
  consistent punishments in one click.
- **Escalation rules** — the warning-count thresholds and the action taken at each.

## Related

- [[Chat Moderation|Chat-Moderation]] — warn/mute/ban directly from live chat.
- [[Players|Players]] — full punishment history per player.
- [[Permissions Reference|Permissions-Reference]] — who can do what.
