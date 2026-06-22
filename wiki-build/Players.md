# Players

Search and manage **every player your server has ever seen** — online or offline. The dashboard keeps
its own player index (populated on join and back-filled from EssentialsX user data), so you can act on
players who aren't currently connected.

**Permissions:** `PLAYERS_VIEW` to browse, `PLAYERS_MANAGE` to edit. Inventory needs
`INVENTORY_VIEW` / `INVENTORY_MANAGE`; mail needs `MAIL_MANAGE`. See
[[Permissions Reference|Permissions-Reference]].

## Player list (`/players`)

- Debounced **search** by name, paginated results.
- Online/offline **status badge** and last-seen time.
- **CSV export** of the current results (UUID, name, online, last seen).
- **Manage** opens the player detail page.

## Player detail (`/players/:uuid`)

A tabbed profile with the player's avatar, status badges (online/offline, banned, muted, OP) and
copy-to-clipboard for UUID and IP.

| Tab | What you can do |
|---|---|
| **Overview** | View nickname, last login, world, gamemode, IP and GeoIP location; add/delete **staff notes**. |
| **Economy** | Set / give / take balance; view the player's transaction history. |
| **Homes & Mail** | List and delete homes; send mail, read and clear the mailbox. |
| **Inventory** | Visual grid editor for inventory, hotbar, armor, off-hand and **ender chest** (online players). |
| **Punishments** | Browse the player's bans, mutes and warnings. |
| **Actions** | Change gamemode, give items, teleport, mute, ban, kick, and send a private message. |

### Offline editing

Balance, nickname, homes, mail, mutes and bans can be changed even when the player is **offline** —
the dashboard writes through EssentialsX's stored user data. Live-only actions (inventory edits,
teleport, gamemode, give, direct messages, kick) require the player to be online.

### Inventory & ender chest editor

The Inventory tab renders a Minecraft-style grid (main inventory, hotbar, armor slots, off-hand and
ender chest). Click a slot to edit the item — material, amount, and full metadata (enchantments, lore,
durability, custom name). Changes apply live to the online player.

### Investigation helpers

- **Staff notes** — private notes attached to a player, with author and timestamp.
- **GeoIP** — country/city from the player's IP when EssentialsXGeoIP is installed (see
  [[EssentialsX Modules|EssentialsX-Modules]]).
- **Alt detection & timeline** — surface accounts sharing an IP and a chronological activity timeline.

## Related

- [[Economy|Economy]] — server-wide money tools and baltop.
- [[Bans, Mutes & Warnings|Bans-Mutes-and-Warnings]] — moderation overview and templates.
- [[Chat Moderation|Chat-Moderation]] — act on players from the live chat feed.
