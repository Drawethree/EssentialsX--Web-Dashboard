# Contributing to EssentialsX Dashboard

Thanks for your interest in improving EssentialsX Dashboard! This guide covers how to set up a
development environment, build the project, and submit changes.

The project is licensed under **GPL-3.0** (see [LICENSE](LICENSE)). By contributing, you agree that
your contributions are licensed under the same terms.

---

## Project layout

```
EssentialsX-Dashboard/
├── pom.xml                         # Maven build (Java plugin + frontend + fat JAR)
├── src/main/java/…/essdash/        # Java plugin (Paper) — embedded web server + REST API
│   ├── api/ApiServer.java          # Javalin server, routing, static SPA hosting
│   ├── api/controllers/            # One controller per feature area
│   └── auth/                       # JWT, permissions, audit log, password hashing
├── src/main/resources/
│   ├── plugin.yml                  # Spigot/Paper plugin descriptor
│   └── config.yml                  # Default config shipped in the JAR
├── src/frontend/                   # Vue 3 + Vite single-page app (the dashboard UI)
│   └── src/{views,components,stores}/
└── wiki-build/                     # Source markdown for the GitHub Wiki
```

**How the pieces fit together:** the Java plugin embeds a [Javalin](https://javalin.io/) HTTP server
(default port `8095`). At build time the Vue SPA is compiled by Vite and packaged into the JAR under
`/web/`; the plugin serves those static assets and exposes the REST API under `/api/*` (plus a
Server-Sent-Events stream at `/api/events/stream`). There is **no separate web app to deploy** — it
all ships as one `EssDashboard.jar`.

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | **17+** (Temurin recommended) | Required to compile and run the plugin |
| Maven | **3.8+** | Build tool |
| Git | any | Version control |
| Node.js / npm | *(automatic)* | Maven installs Node `20.11.0` + npm `10.2.4` into `target/node` during the build — you don't need Node installed globally unless you want the frontend dev server |

A local **Paper/Spigot 1.17+** server with **EssentialsX 2.19+** installed is needed to actually run
and test the plugin.

---

## Building

Build the full plugin JAR (compiles Java **and** the frontend, then shades everything into one JAR):

```bash
mvn clean package
```

Output: `target/EssentialsX-Dashboard-<version>.jar`.

To skip the frontend build during quick Java-only compiles:

```bash
mvn clean package -Dfrontend.skip=true
```

## Frontend development

For fast UI iteration, run the Vite dev server (hot reload) against a running backend:

```bash
cd src/frontend
npm install
npm run dev
```

By default the dev server proxies API calls to a dashboard running on `http://localhost:8095` — see
`src/frontend/vite.config.js`. Point it at your test server if it runs elsewhere.

Other scripts: `npm run build` (production build) and `npm run preview` (preview a production build).

## Testing your change on a server

1. Run `mvn clean package`.
2. Copy `target/EssentialsX-Dashboard-<version>.jar` into your test server's `plugins/` folder.
3. Make sure **EssentialsX** is installed (it's a hard dependency).
4. Start the server and open `http://localhost:8095`.

---

## Coding guidelines

- **Java:** target Java 17. Match the existing style (4-space indent, package
  `dev.drawethree.essdash`). Keep controllers thin — one feature area per controller under
  `api/controllers/`. Never log secrets (JWT tokens, the `jwt.secret`, Discord tokens).
- **Frontend:** Vue 3 Composition API (`<script setup>`), Tailwind for styling, Pinia for state.
  Reuse the shared components in `src/frontend/src/components/` (Button, Modal, Pagination, etc.)
  rather than re-rolling them.
- **Security first:** any new endpoint that mutates state must check the appropriate `Permission`
  and be written to the audit log. Follow the patterns already in the controllers.
- **Docs:** if you add or change a feature, update the relevant page(s) in `wiki-build/`.

## Pull requests

1. Fork the repo and create a topic branch (`feature/…` or `fix/…`).
2. Keep PRs focused; describe **what** changed and **why**.
3. Make sure `mvn clean package` succeeds before opening the PR — CI runs the same build.
4. Reference any related issue (e.g. `Fixes #123`).
5. Fill in the pull-request template.

## Reporting bugs / requesting features

Use the [issue templates](https://github.com/Drawethree/EssentialsX--Web-Dashboard/issues/new/choose).
For **security vulnerabilities**, do **not** open a public issue — see [SECURITY.md](SECURITY.md).

Thanks for contributing! 🎉
