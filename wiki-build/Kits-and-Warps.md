# Kits & Warps

Manage EssentialsX **kits** and **warps** visually — no editing `config.yml` by hand.

**Permissions:** `KITS_VIEW` / `KITS_MANAGE` and `WARPS_VIEW` / `WARPS_MANAGE`.

## Kits (`/kits`)

- Card-based view of every kit, with **item icons** rendered from material names and the kit's
  cooldown/delay.
- Create or edit a kit in a modal: set the **delay** (cooldown, in seconds) and the **items** using
  EssentialsX item syntax (material, amount, enchantments, meta).
- Delete a kit with confirmation.

Changes are written back to EssentialsX, so kits work in-game immediately (`/kit <name>`).

## Warps (`/warps`)

- Table of all warps with name, world and coordinates.
- Create or edit a warp by setting its location and rotation (world, X, Y, Z, yaw, pitch).
- **Teleport** an online player to a warp.
- Delete a warp with confirmation.

## Related

- [[Server Controls|Server-Controls]] — spawn and jail locations.
- [[Players|Players]] — give items / teleport individual players.
