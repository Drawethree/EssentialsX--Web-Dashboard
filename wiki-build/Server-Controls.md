# Server Controls

Administer the running server: whitelist, worlds, lifecycle, spawn and jails.

**Permission:** `SERVER_MANAGE` (this whole page is gated by it).

## Page (`/server`)

### Whitelist
- Toggle the whitelist on/off.
- Add or remove players.

### Worlds
- List all worlds with environment, time, weather and online count.
- Set **time** (presets such as day/night/noon/midnight).
- Set **weather** (clear / rain / thunder).

### Lifecycle
- **Save-all** — flush all worlds and player data to disk.
- **Stop** — shut the server down (requires explicit confirmation).

> For *scheduled* or countdown restarts, use the [[Scheduler|Scheduler]] (RESTART task) rather than a
> raw stop.

### Spawn *(requires EssentialsXSpawn)*
- View and set the spawn location. Only available when EssentialsXSpawn is installed (see
  [[EssentialsX Modules|EssentialsX-Modules]]).

### Jails
- Create and delete jails (name + location).
- Jail and unjail players.

## Related

- [[Live Console|Live-Console]] — run arbitrary commands.
- [[Scheduler|Scheduler]] — automate restarts, broadcasts and commands.
- [[EssentialsX Modules|EssentialsX-Modules]] — optional add-ons that unlock extra controls.
