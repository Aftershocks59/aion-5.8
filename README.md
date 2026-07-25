# Aion 5.8 Emulator

Server emulator for Aion 5.8, modernised to run on **Java 21** with a **Gradle**
build and **MariaDB**.

Three servers make up a running installation:

| Module | Role | Ports |
| --- | --- | --- |
| `AL-Login` | Authenticates accounts, hands the client its server list | 2106 (client), 9014 (game server) |
| `AL-Game` | Runs the world: players, NPCs, quests, instances, sieges | 7777 (client) |
| `AL-Chat` | Serves chat channels and broadcasting | 9021 |
| `AL-Commons` | Shared library: networking, database access, runtime script compiler | — |

## Requirements

- JDK 21
- MariaDB 10.6 or later, or MySQL 8
- Geodata files, distributed separately (see [Geodata](#geodata))

Gradle itself needs no installation: use the wrapper (`./gradlew`).

On Windows, let git write long paths before cloning:

```bash
git config --global core.longpaths true
```

Some quest and AI script names run to 129 characters. Without this, cloning
into anything but a short directory stops partway through with "Filename too
long", having already reported success.

## Build

```bash
./gradlew build           # compile and package every module
./gradlew test            # run the unit tests
./gradlew compileScripts  # compile the runtime script contexts
```

`compileScripts` runs as part of `build`. `AL-Game/data/scripts/` holds thousands
of quest, AI and instance sources, one script context per directory, each loaded
into its own classloader. Each context is compiled separately here for the same
reason: a shared compilation would accept references across contexts that the
runtime classloaders reject. One bad file aborts its whole context, so a broken
quest script used to take all 6196 quests down with it, and only a full boot
revealed it.

The task also packages each context into `cache/scripts/<context>.jar`. The
server loads those archives instead of compiling at startup, which is worth
roughly twenty seconds. An archive is used only while it is newer than every
source in its context, so editing a script recompiles that context on the next
start and leaves the others alone. A missing, stale or damaged archive only
means a slower start, never a broken one.

## Database

Create the two empty schemas and a dedicated user. Do not import any SQL by
hand: each server builds and updates its own schema when it starts.

```sql
CREATE DATABASE al_server_ls CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE al_server_gs CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'aion'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON al_server_ls.* TO 'aion'@'localhost';
GRANT ALL PRIVILEGES ON al_server_gs.* TO 'aion'@'localhost';
```

### Migrations

Each server owns its schema and migrates it with Flyway before opening a single
connection, so no query ever meets a database older than the code. A migration
that fails stops the server rather than letting it run against a schema it
disagrees with.

```
AL-Login/sql/migration/    V1__baseline_schema.sql
AL-Game/sql/migration/     V1__baseline_schema.sql
                           V2__remove_orphan_player_quests.sql
                           V3__add_tasks_last_activation.sql
AL-Game/sql/maintenance/   operator scripts, run by hand, never migrations
```

To add one, drop a `V<n>__<what_it_does>.sql` file in the right directory and
take the next free number. `MigrationFilesTest` fails the build on a name Flyway
would silently skip, and on two files claiming the same version.

Never edit a migration that has already run: Flyway records a checksum and
refuses to continue when a file changes underneath it. Correct it with a new
one.

An **empty** schema is built from `V1`. An **existing** schema is stamped at
version 1 and only the later migrations run, so upgrading an installed server
never recreates its tables.

`sql/maintenance` holds recurring operator scripts, such as the abyss GP decay.
They deliberately sit outside the migration path: a migration runs once per
database, which is not what a recurring job needs.

Set `database.migration.enable = false` in the database configuration to apply
them yourself instead, for instance when several servers share one schema and
only one should write to it.

## Persistence

Every query lives in a repository: an interface naming what the game asks for,
and a `Jdbc*Repository` that answers it. Each takes its `DataSource` by
constructor, so a test hands it a mocked one and no test needs a database.

```
AL-Game/src/…/repository/     PlayerRepository        + JdbcPlayerRepository
                              InventoryRepository     + JdbcInventoryRepository
                              …                       GameRepositories
AL-Login/src/…/repository/    AccountRepository       + JdbcAccountRepository
                              …                       LoginRepositories
```

`GameRepositories` and `LoginRepositories` hand out the set, one typed accessor
each, built lazily over the connection pool once the server has started:

```java
Player owner = GameRepositories.players().load(playerId);
GameRepositories.inventories().save(owner);
```

A repository throws `RepositoryException` when the database refuses it, rather
than logging and answering an empty list. Callers that must keep going catch it;
the rest let it travel, which is what makes a lost write visible instead of
silent. State on an object is marked as saved only after the write has landed,
so a failed write is retried on the next save rather than forgotten.

## Credentials

**Never put a real password in a tracked configuration file.** The files under
`config/network/` ship harmless defaults written as `${NAME:default}`
placeholders, resolved at startup against JVM system properties first, then
environment variables.

```bash
export AION_DB_USER=aion
export AION_DB_PASSWORD=your-password
export AION_DB_HOST=localhost        # optional
export AION_DB_PORT=3306             # optional
```

## Run

```bash
./gradlew :AL-Login:runServer -PAION_DB_USER=aion -PAION_DB_PASSWORD=your-password
./gradlew :AL-Game:runServer  -PAION_DB_USER=aion -PAION_DB_PASSWORD=your-password
```

Start the login server first: the game server registers against it on port 9014.
Point the client at `127.0.0.1:2106`. With
`loginserver.accounts.autocreate = true`, the first login creates the account.

## Geodata

Geodata is not distributed with the sources. Until you install it under
`AL-Game/data/geo/`, leave `gameserver.geodata.enable = false` in
`AL-Game/config/main/geodata.properties`: the server then runs without collision
detection or line of sight, but logins, character creation and world entry all
work. Enabling it without the files aborts the start.

## Static data cache

`AL-Game/data/static_data/` holds several hundred megabytes of XML. Parsing it
dominated startup, so the parsed graph is snapshotted to
`AL-Game/cache/static_data.bin` after the first successful parse and reused on
every later start. A checksum per source file decides: touch any XML and the
snapshot is rebuilt automatically.

Delete `AL-Game/cache/` to force a rebuild.

## License

GNU Lesser General Public License v3.

Built on the work of the Aion-Lightning and Encom teams and their contributors.
