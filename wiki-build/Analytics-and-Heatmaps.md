# Analytics & Heatmaps

The **Analytics** page (`/analytics`) turns the metrics the plugin collects over time into readable
trends, plus two big-picture views: a **login activity heatmap** and a **player geography** breakdown.
It's available to **any logged-in user** (no special permission required).

> 📸 **Screenshot:** _Analytics page — trend charts, the login heatmap, and player geography._
> _(See [how to add screenshots](Images).)_

## How data is collected

A built-in metrics sampler records a snapshot **every 5 minutes** into the dashboard's SQLite database
(`dashboard.db`). Trends therefore start empty on a fresh install and fill in as the server runs — if
you just installed the plugin, expect a brief **"No data yet"** state.

## Trend charts

Pick a **time range** in the top-right — **Last hour**, **Last 24h**, **Last 7 days**, or
**Last 30 days** — and every chart re-scales to it. Each card shows a line chart plus **Current /
Average / Peak** for the range.

| Metric | What it tracks |
|---|---|
| **Players Online** | Concurrent player count |
| **TPS** | Server ticks per second (capped at 20) |
| **Memory (MB)** | Heap memory in use |
| **Total Economy** | Total money in circulation (uses your currency symbol) |
| **Loaded Chunks** | Chunks currently loaded *(appears once samples include it)* |
| **Entities** | Live entity count *(appears once samples include it)* |

> The **Loaded Chunks** and **Entities** charts only show up after the sampler has recorded at least
> one value for them, so servers upgrading from an older version won't see empty charts for old data.

## Login activity heatmap

A **7 × 24 grid** (weekday × hour) showing **when players log in**. Darker/brighter cells mean more
logins in that weekday-and-hour bucket, so you can spot your server's busy times at a glance — useful
for scheduling [[restarts and events|Scheduler]] when the fewest players are online.

The heatmap spans all retained login history (independent of the trend range selector above). The
underlying data can be aggregated **server-wide** (the Analytics page) or scoped to a **single
player** — a player's own activity pattern also appears on their [[profile|Players]].

## Player geography

A country-level breakdown of where your players connect from, powered by **EssentialsXGeoIP**:

- If **EssentialsXGeoIP** is installed, you get a ranked list of countries with player counts.
- If it isn't, the panel says so and links you to install it — see
  [[EssentialsX Modules|EssentialsX-Modules]].

Per-player location (country/city) also appears on the [[player profile|Players]] Overview tab. On a
public **demo** instance, IP-derived data is privacy-masked — see
[[Permissions Reference|Permissions-Reference]].

## Overview sparklines

The [[dashboard home page|Dashboard-Overview-and-Analytics]] shows compact **sparklines** for the key
metrics (online, TPS, memory, players) drawn from the same sampled data — a quick pulse without
opening the full Analytics page.

## Related

- [[Dashboard & Overview|Dashboard-Overview-and-Analytics]] — the live home-page snapshot.
- [[Economy|Economy]] — money-supply trends and top earners/spenders.
- [[EssentialsX Modules|EssentialsX-Modules]] — enable GeoIP for the geography view.
