# PlayerLog

Lightweight per-player note tracking for a Paper server. Notes are stored
per-player, keyed by UUID, in a local SQLite database.
Git-style logging for players: append-only entries with author & timestamp with no editing,
only append or delete.

## Building

Requirements:
- Requires JDK 21 and Maven.

```bash
mvn clean package
```

The relevant jar is `target/PlayerLog.jar`. sqlite-jdbc is shaded in and
its `org.sqlite` package is relocated to `com.luigi.playerlog.libs.sqlite`
so it won't collide with CoreProtect's bundled sqlite-jdbc on the same
server.

Before building, check `pom.xml` and bump the `paper-api` version to
match whatever your server is actually running (`1.21.1-R0.1-SNAPSHOT` is
just a placeholder).

## Installing

Drop `PlayerLog.jar` into `plugins/` and restart (or `/reload confirm`,
though a restart is safer). It creates `plugins/PlayerLog/logs.db` on
first enable.

## Usage

```
/playerlog log <player> <message...>   - append a note about a player
/playerlog readlog <player> [page]     - view a player's notes, newest first (8/page)
/playerlog clearlog <player>           - wipe all notes for a player
```

Alias: `/plog`.

Works for offline players as well!

## Permissions

| Node               | Default | Grants                  |
|---------------------|---------|--------------------------|
| `playerlog.log`      | op      | `/playerlog log`         |
| `playerlog.read`     | op      | `/playerlog readlog`     |
| `playerlog.clear`    | op      | `/playerlog clearlog`    |
| `playerlog.admin`    | op      | everything               |

## Notes/Extra features:

- **Async by default**: every DB read/write runs on a scheduler async
  task, which goes back to the main thread only to send chat feedback.
- **Tab completion is cached**: known player names and per-player page
  counts are cached in memory and updated on every write, so the only delay is on startup.
- **Immutable entries**: there's intentionally no `editlog` — if you
  want to correct something, log a new entry. Keeps the history honest,
  same as a commit log.
  If you'd like to see log editing implemented, let me know through an issue.