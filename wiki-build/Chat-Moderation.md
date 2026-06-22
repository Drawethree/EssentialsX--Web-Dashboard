# Chat Moderation

Watch chat live and act on problem messages without opening the console.

**Permissions:** `CHAT_VIEW` to read the feed and history, `CHAT_MODERATE` to delete lines and act on
players.

## Page (`/chat`)

- **Live feed** — in-game chat streams in real time over the SSE connection.
- **Searchable history** — chat is persisted, so you can search past messages by player name or text
  (paginated, newest first).
- **Flagging** — messages containing configured keywords are highlighted for attention.
- **Inline actions** (on hover): **Warn**, **Mute** (60-minute default), **Ban**, and **Delete line**.
- Each message links to the player's [[profile|Players]].

## Deleting messages

Deleting a chat line is a **soft delete** — it's hidden from the dashboard's chat views but kept for
the record and noted in the [[audit log|Audit-Log]].

## Retention

Chat history is pruned automatically (older entries are removed on a rolling basis), so the database
doesn't grow unbounded.

## Related

- [[Bans, Mutes & Warnings|Bans-Mutes-and-Warnings]] — punishment templates and escalation.
- [[Live Console|Live-Console]] — full server output and command execution.
