# Broadcast & Mail

One-off messaging tools for reaching your players right now. For scheduled/recurring messages, use the
[[Scheduler|Scheduler]].

**Permissions:** the page (`/tools`) is available to anyone with `BROADCAST` **or** `MAIL_MANAGE`; each
tool is gated by its own permission.

## Broadcast *(requires `BROADCAST`)*

Send a message to **all online players**. Supports `&` color codes, with a live preview so you can see
the formatting before sending.

## Mail everyone *(requires `MAIL_MANAGE`)*

Send mail to **every known player** (online or not). Recipients read it in-game with `/mail read`.
Also supports color-code preview.

Both actions are recorded in the [[audit log|Audit-Log]].

## Related

- [[Scheduler|Scheduler]] — automate recurring broadcasts and mail.
- [[Players|Players]] — message or mail an individual player.
