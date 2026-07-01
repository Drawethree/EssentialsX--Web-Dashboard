# Security Policy

EssentialsX Dashboard is a control panel that can execute privileged actions on a Minecraft server
(managing players, the economy, the console, and staff accounts). We take security seriously and
appreciate responsible disclosure.

## Supported versions

Security fixes are provided for the **latest released version**. Please make sure you can reproduce
an issue on the newest release before reporting.

| Version | Supported |
|---|---|
| Latest release | ✅ |
| Older releases | ❌ (please upgrade) |

## Reporting a vulnerability

**Please do not report security issues in public GitHub issues, pull requests, or discussions.**

Instead, report privately through **GitHub Private Vulnerability Reporting**:

1. Go to the repository's **Security** tab →
   [**Report a vulnerability**](https://github.com/Drawethree/EssentialsX--Web-Dashboard/security/advisories/new).
2. Describe the issue, the affected version, and clear steps to reproduce (proof-of-concept if
   possible) and the impact.

You can expect an initial acknowledgement within a few days. Once a fix is available we will
coordinate a release and credit you (unless you prefer to remain anonymous).

> Maintainers: enable **Settings → Code security and analysis → Private vulnerability reporting** so
> the link above works.

## Scope & hardening notes

The dashboard is designed to be deployed securely, but operators are responsible for their
deployment. Please review the production checklist in the
[Wiki](https://github.com/Drawethree/EssentialsX--Web-Dashboard/wiki/First-Login-and-Security) and
[Reverse Proxy & HTTPS](https://github.com/Drawethree/EssentialsX--Web-Dashboard/wiki/Reverse-Proxy-and-HTTPS)
guide. In particular:

- The dashboard speaks **plain HTTP** and expects TLS to be terminated by a reverse proxy.
- `plugins/EssDashboard/config.yml` contains the auto-generated **`jwt.secret`**. Anyone who can read
  it can forge sessions — protect it with filesystem permissions.
- The live-console SSE stream authenticates via a `?token=` query parameter; make sure your proxy
  does not log query strings.
- Change the default `admin` / `changeme` password before exposing the panel and enable 2FA.

Thank you for helping keep EssentialsX Dashboard and its users safe.
