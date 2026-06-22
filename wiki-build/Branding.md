# Branding

White-label the dashboard to match your server. The **Branding** page (`/branding`) is **admin only**.

## What you can customize

- **Server name** — shown in the sidebar, on the login screen, and in the browser tab.
- **Accent color** — applied to buttons, badges and links. Pick a hex value or choose from presets
  (red, blue, green, purple, orange, cyan, pink, slate). A live preview shows a button, badge and
  accent text in your chosen color.
- **Logo** — upload a custom image (PNG, JPG, GIF, WEBP or SVG, up to ~1 MB) shown on the login screen
  and sidebar. Remove it to fall back to the default.

The server name, accent color and "has logo" flag are exposed on a public endpoint so the **login
screen** can be branded before anyone signs in (see [[REST API Reference|REST-API-Reference]]).

## Themes

Every user can toggle **dark / light** mode from the UI; the choice is remembered in their browser.
Your accent color applies in both themes.

Branding changes are recorded in the [[audit log|Audit-Log]].

## Related

- [[Configuration|Configuration]] — server-side settings.
- [[Staff & Permissions|Staff-Accounts-and-Permissions]] — admin-only pages.
