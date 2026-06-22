# Staff Accounts & Permissions

Give your team scoped access to the dashboard. Admins manage accounts on the **Staff** page
(`/staff`, admin only).

## Roles

| Role | Access |
|---|---|
| **ADMIN** | Full access to everything. Bypasses individual permission checks. |
| **STAFF** | Access only to the permissions you explicitly grant. |
| **DEMO** | Read-only showcase account for evaluation. Can *view* almost the whole panel (every feature page plus the admin pages), but **all non-GET requests are blocked** and **IP addresses are masked**. See [[Permissions Reference|Permissions-Reference]] and [[Configuration|Configuration]] → `demo`. |

## Managing staff (`/staff`)

- **List** every account with its role, 2FA status and permission count.
- **Create** a staff account: username, password, role, and a set of permission checkboxes.
- **Edit** an account's password, role and permissions.
- **Delete** an account.
- **Reset 2FA** for any user who's locked out of their authenticator.
- **Active sessions** — view all logged-in sessions across all users (username, role, IP, device, last
  seen), revoke any individual session, or log everyone else out.

## Permissions

Permissions are granular and per-feature (view vs. manage). Assign only what each role needs. The full
list of permission keys, what they unlock, and which page they gate is on the
**[[Permissions Reference|Permissions-Reference]]** page.

A few patterns:
- `*_VIEW` permissions grant read access to a feature; `*_MANAGE` (or `*_EXECUTE`/`*_MODERATE`) grant
  the ability to change things.
- ADMIN needs no permissions assigned — it has them all.
- Some pages are **admin-only** regardless of permissions: Staff, [[Branding|Branding]], and the
  moderation-settings page.

## Related

- [[First Login & Security|First-Login-and-Security]] — each user secures their own account & 2FA.
- [[Permissions Reference|Permissions-Reference]] — the complete permission catalog.
- [[Audit Log|Audit-Log]] — staff actions are recorded with the acting username.
