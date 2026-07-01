# Live Console

A real-time view of your server console, right in the browser.

**Permissions:** `CONSOLE_VIEW` to watch output, `CONSOLE_EXECUTE` to run commands. Command execution
is also gated by [[Configuration|Configuration]] (`console.allow-commands`).

> 📸 **Screenshot:** _The live console streaming server output with a command input._
> _(See [how to add screenshots](Images).)_

## Page (`/console`)

- **Live output** — console lines stream in over the SSE connection as they're logged, shown in a
  dark terminal panel (in both light and dark themes).
- **Color-coded levels** — ERROR / WARN / INFO / DEBUG are visually distinct.
- **Timestamps toggle** — optionally prefix each line with the time it was logged.
- **Chat toggle** — show or hide in-game chat messages mixed into the stream.
- **Clear** — wipe the on-screen buffer.
- **Run commands** — type a command and run it as the server console (when permitted).
- **Command history** — press up/down to recall previous commands.
- **Buffer & smart auto-scroll** — keeps the most recent ~1000 lines and follows new output;
  scrolling up pauses auto-scroll and shows a **Jump to latest** button to resume.
- A **read-only indicator** appears when you lack `CONSOLE_EXECUTE`, and a connection-status indicator
  shows the live link state.

## Blocked commands

Commands listed in `console.blocked-commands` (default: `stop`, `restart`, `op`, `deop`) can never be
run from the web console — matched on the first word, case-insensitively, with any `namespace:` prefix
stripped (so `minecraft:stop` and `/stop` both match `stop`). **Blocked attempts are still recorded**
in the [[audit log|Audit-Log]]. Scheduled [[Scheduler|Scheduler]] command tasks honour the same list.

To disable command execution entirely (read-only console), set `console.allow-commands: false` in
[[Configuration|Configuration]].

## Notes

- Console output is delivered via the live event stream — it is not returned as a command response.
- For graceful, scheduled restarts, use the [[Scheduler|Scheduler]] instead of running `stop`/`restart`.

## Related

- [[Server Controls|Server-Controls]] — save-all, stop, whitelist, worlds.
- [[Chat Moderation|Chat-Moderation]] — a focused view of just chat.
