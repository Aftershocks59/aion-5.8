# Aion 5.8 Emulator

Server emulator for Aion 5.8, modernised to run on **Java 21** with a **Gradle**
build and **MariaDB**.

Three servers make up a running installation:

| Module | Role | Ports |
| --- | --- | --- |
| `AL-Login` | Authenticates accounts, hands the client its server list | 2106 (client), 9014 (game server) |
| `AL-Game` | Runs the world: players, NPCs, quests, instances, sieges | 7777 (client) |
| `AL-Chat` | Serves chat channels and broadcasting | 9021 |
| `AL-Commons` | Shared library: networking, DAO layer, runtime script compiler | — |

## Requirements

- JDK 21
- MariaDB 10.6 or later, or MySQL 8
- Geodata files, distributed separately (see [Geodata](#geodata))

Gradle itself needs no installation: use the wrapper (`./gradlew`).

## Build

```bash
./gradlew build          # compile and package every module
./gradlew test           # run the unit tests
```

## Database

Create the two schemas and a dedicated user, then import the shipped SQL:

```sql
CREATE DATABASE al_server_ls CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE al_server_gs CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'aion'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON al_server_ls.* TO 'aion'@'localhost';
GRANT ALL PRIVILEGES ON al_server_gs.* TO 'aion'@'localhost';
```

```bash
mariadb -u aion -p al_server_ls < AL-Login/sql/al_server_ls.sql
mariadb -u aion -p al_server_gs < AL-Game/sql/al_server_gs.sql
```

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
