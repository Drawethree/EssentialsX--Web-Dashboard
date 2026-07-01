# Building from Source

The project is a single Maven module that bundles the **Java plugin** and the **Vue 3 frontend** into
one fat jar. The frontend is built automatically during the Maven build via the
`frontend-maven-plugin` (which downloads its own Node/npm), so you don't need Node installed globally.

## Prerequisites

- **JDK 21** — required to compile against the Paper 1.21.8 API (its class files are Java 21). Don't
  worry: the build still targets **Java 17 bytecode**, so the plugin runs on Java 17+ servers. Building
  with an older JDK fails with `class file has wrong version 65.0, should be 61.0`.
- **Maven 3.8+**
- Internet access on first build (downloads Paper/EssentialsX APIs, Node v20.11.0 / npm 10.2.4, and npm
  packages)

## Build

```bash
git clone https://github.com/Drawethree/EssentialsX--Web-Dashboard.git
cd EssentialsX--Web-Dashboard
mvn clean package
```

The output jar is written to:

```
target/EssentialsX-Dashboard-1.1.0.jar
```

Copy it into your server's `plugins/` folder (see [[Installation|Installation]]).

### Java-only builds (skip the frontend)

When you only changed Java code, skip the npm install/build to save time:

```bash
mvn clean package -Dfrontend.skip=true
```

> Note: skipping the frontend produces a jar **without** the compiled web UI bundle. Use a full build
> for anything you actually deploy.

## What the build does

| Stage | Tool | Result |
|---|---|---|
| Compile Java | `maven-compiler-plugin` (Java 17) | Plugin classes |
| Install Node/npm | `frontend-maven-plugin` | Local `target/node` (Node v20.11.0) |
| `npm install` | npm | Frontend dependencies |
| `npm run build` | Vite | UI bundle into `target/classes/web` |
| Package | `maven-shade-plugin` | Single fat jar with relocated libraries |

### Shaded & relocated libraries

To avoid clashing with the server's own copies, the shade plugin relocates: Jackson, JJWT, BCrypt and
MaxMind GeoIP2. SQLite (`org.xerial`) and Javalin/Jetty/Kotlin are intentionally **not** relocated
(native-lib extraction and internal reflection require their original package paths).

## Tech stack

- **Backend:** Java 17, [Javalin 6](https://javalin.io/) (embedded Jetty), Jackson, JJWT, jBCrypt,
  SQLite (xerial JDBC), MaxMind GeoIP2.
- **Frontend:** Vue 3.5 (Composition API), Vite 6, Vue Router 4, Pinia 2, Tailwind CSS 3, Reka UI,
  Heroicons, CodeMirror 6 (YAML editor), axios, vue-i18n, qrcode.

See [[REST API Reference|REST-API-Reference]] for the API the frontend consumes.
