# First Login & Security

This page covers securing **your own** account. For creating other staff accounts and assigning
permissions, see [[Staff & Permissions|Staff-Accounts-and-Permissions]].

## First login & forced password change

On first start the plugin bootstraps the `admin` account from `config.yml`. If the password is still
the default `changeme`, the dashboard **forces a password change** on first login before you can use
anything else.

## Changing your credentials

Open the user menu (top-right) → **Change Credentials** to update your username and/or password at any
time. Passwords are stored **bcrypt-hashed** (12 rounds) — never in plain text. Changing your password
issues a fresh session token.

## Two-factor authentication (2FA / TOTP)

Add a second factor with any authenticator app (Google Authenticator, Authy, 1Password, etc.).

**Enable it** (user menu → **Security**):
1. Click **Set up 2FA** and confirm your current password.
2. Scan the QR code into your authenticator app.
3. Enter a 6-digit code to confirm.
4. **Save the recovery codes** shown — 10 single-use codes, displayed only once. Store them somewhere
   safe; each can be used in place of a TOTP code if you lose your device.

**Logging in with 2FA:** after username + password, you're prompted for a 6-digit code (or a recovery
code). The intermediate step uses a short-lived (5-minute) pre-auth token that can't access anything
else.

**Disable / regenerate:** the Security page lets you disable 2FA or regenerate recovery codes (both
require your password).

**Locked out?** An admin can reset any staff member's 2FA from
[[Staff & Permissions|Staff-Accounts-and-Permissions]]. The bootstrap admin can also be recovered via
the database — see [[Troubleshooting & FAQ|Troubleshooting-and-FAQ]].

## Sessions

Every login is a tracked session (stored server-side so it can be revoked). On the **Security** page
you can:

- See all your active sessions with device, IP and last-seen time (your current device is badged).
- Revoke an individual session.
- **Log out everywhere else** in one click.

Admins can view and revoke **all** users' sessions from the Staff page.

## Built-in protections

| Protection | Detail |
|---|---|
| Password hashing | bcrypt, 12 rounds |
| Session tokens | Signed JWT (HS256) with a server-generated secret; checked against a server-side session store so they can be revoked |
| Session expiry | `jwt.expiry-hours` (default 24h) |
| Login rate-limiting | 5 failed attempts per IP per minute → `429 Too Many Requests` |
| 2FA rate-limiting | 5 failed codes per account per 5 minutes (covers TOTP **and** recovery codes) → `429` |
| 2FA | Optional TOTP per account, with hashed single-use recovery codes |
| Audit trail | Logins, failures, 2FA changes, session revocations and more are recorded — see [[Audit Log|Audit-Log]] |
| Security headers | `Content-Security-Policy`, `Strict-Transport-Security` (HSTS), `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy` on every response |

## Demo mode

If `demo.enabled: true` in [[Configuration|Configuration]], a read-only demo account is available
(one-click login on the sign-in page). It's designed for evaluation, so it can **browse almost the
entire panel** — every feature page plus the admin-only Staff, Branding and Moderation-settings pages
— while being **strictly read-only**: all non-GET requests are blocked, so nothing can be created,
edited, executed or deleted.

To protect privacy on a public demo, **IP addresses are masked** for demo viewers everywhere they'd
appear (player profiles, GeoIP, login history, alt detection, sessions, audit log and the live
console) — replaced with a stable token like `ip#a1b2c3`. See
[[Permissions Reference|Permissions-Reference]] for the exact demo access set.

You can try it on the live demo instance: **http://213.170.135.173:8026/**
