# Adding screenshots to the wiki

Several wiki pages have **screenshot placeholders** (📸 callouts) where an image would help. This page
explains how to add the real screenshots — it's for contributors/maintainers, not end users.

## Where screenshots live

Screenshots go in the **`wiki-build/images/`** folder in the main repository. The
`publish-wiki` workflow syncs `wiki-build/` (including `images/`) into the GitHub Wiki automatically on
every change to `main`.

## How to capture them

Use a clean, branded panel with the **demo data** so no real player info is exposed:

- The public **live demo** (linked from the [[Home]] page) is ideal — log in with the one-click demo
  account, which masks IP addresses automatically.
- Or run your own instance and use `demo.enabled: true` (see [[Configuration|Configuration]]).

Capture at a consistent width (≈1440px), in **dark theme**, and crop to the relevant panel. Save as
optimized **PNG** (or WEBP) and keep file sizes reasonable.

## How to embed them

Once a screenshot exists, replace the placeholder callout on the page with a Markdown image:

```markdown
![Analytics page](images/analytics.png)
```

## Suggested shot list

| File | Page | Shows |
|---|---|---|
| `overview.png` | [[Dashboard & Overview|Dashboard-Overview-and-Analytics]] | Stat cards, sparklines, online players |
| `analytics.png` | [[Analytics & Heatmaps|Analytics-and-Heatmaps]] | Trend charts + heatmap + geography |
| `login.png` | [[Installation|Installation]] | Sign-in screen / first-login password change |
| `players.png` | [[Players|Players]] | Player list and a player profile |
| `inventory.png` | [[Players|Players]] | Inventory / ender-chest editor |
| `console.png` | [[Live Console|Live-Console]] | Live console with output |
| `economy.png` | [[Economy|Economy]] | Baltop + stats |
| `staff.png` | [[Staff & Permissions|Staff-Accounts-and-Permissions]] | Staff accounts + permissions |

Add more as features evolve — just drop the file in `images/` and reference it from the relevant page.
