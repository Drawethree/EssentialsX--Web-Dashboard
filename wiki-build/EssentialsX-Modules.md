# EssentialsX Modules

The dashboard auto-detects installed EssentialsX add-ons and exposes a panel for each one it can
manage. Modules that aren't installed are shown as unavailable.

**Permissions:** `MODULES_VIEW` to view, `MODULES_MANAGE` to edit module configs.

## Page (`/modules`)

A detection hub lists each module and whether it's installed (with version where available):

| Module | Plugin | What you can manage |
|---|---|---|
| **Chat** | EssentialsXChat | Default chat format and per-group format overrides, with a color-code preview. |
| **Protect** | EssentialsXProtect | The Protect settings, edited as YAML. |
| **Discord** | EssentialsXDiscord | The Discord integration config, edited as YAML (**bot token is masked**). |
| **Spawn** | EssentialsXSpawn | Spawn location get/set — surfaced on [[Server Controls|Server-Controls]]. |
| **GeoIP** | EssentialsXGeoIP | Player country/city lookups — surfaced on the [[player profile|Players]]. |
| **AntiBuild** | EssentialsXAntiBuild | Detected and listed (read-only status). |

## Editing configs

- **Chat** has a structured editor for the format string and group formats, with a live color preview
  (`&`/`§` codes).
- **Protect** and **Discord** use the same CodeMirror **YAML editor** as
  [[Configuration|Configuration]] — with syntax highlighting and validation before save.
- For **Discord**, the bot token is masked in the editor; leaving the mask in place preserves the
  existing token on save so you never expose or accidentally wipe it.

Every module config save is audited.

## Related

- [[Configuration|Configuration]] — the dashboard's own settings.
- [[Players|Players]] — GeoIP location appears on a player's Overview tab.
- [[Server Controls|Server-Controls]] — spawn management via EssentialsXSpawn.
