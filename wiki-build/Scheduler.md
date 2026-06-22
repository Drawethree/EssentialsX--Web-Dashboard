# Scheduler

Automate recurring or one-off server actions — broadcasts, commands, mail-all and restarts — without
external cron.

**Permissions:** `SCHEDULER_VIEW` to view, `SCHEDULER_MANAGE` to create/edit/delete and run tasks.

## Page (`/scheduler`)

A table of scheduled tasks showing name, type, schedule, next run and last result.

### Task types

| Type | Action |
|---|---|
| `BROADCAST` | Send a message to all online players. |
| `COMMAND` | Run a console command. Subject to the console's `console.blocked-commands` list — a blocked command is rejected when you save the task. |
| `MAIL_ALL` | Send mail to every known player. |
| `RESTART` | Restart the server, with an optional countdown. |

### Schedule types

- **ONCE** — run a single time at a chosen moment.
- **INTERVAL** — run repeatedly on a fixed interval.

### Managing tasks

- **Create / edit** in a modal (name, type, payload, schedule).
- **Run now** — execute immediately without changing the schedule.
- **Toggle** — pause or resume a task.
- **Delete** — remove it (with confirmation).

Tasks persist in the dashboard database and resume after a server restart. Each run records its result,
and task changes are audited.

## Related

- [[Server Controls|Server-Controls]] — manual save/stop and lifecycle.
- [[Broadcast & Mail|Tools-Broadcast-and-Mail]] — send a one-off broadcast or mail right now.
