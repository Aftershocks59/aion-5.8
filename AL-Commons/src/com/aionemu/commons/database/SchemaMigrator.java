/**
 * This file is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * It is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License along with
 * it. If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.commons.database;

import java.io.File;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.configs.DatabaseConfig;

/**
 * Brings a schema up to date from the versioned SQL under the server's migration
 * directory.
 * <p>
 * The schema used to be a hand-applied dump plus a folder of loose ALTER scripts
 * with no order and no record of what had run. Nothing could tell whether a
 * database matched the code about to talk to it.
 * <p>
 * Each server owns its own database and its own migrations, so this takes the
 * directory to read rather than assuming one.
 *
 * @author Oraion
 */
public final class SchemaMigrator {

	private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);

	/**
	 * Names the version an already populated database is stamped with.
	 * <p>
	 * V1 is the dump every existing install was built from. Stamping rather than
	 * running it is what stops Flyway recreating the tables of a live server.
	 */
	private static final String BASELINE_VERSION = "1";

	private SchemaMigrator() {
	}

	/**
	 * Applies every migration the database has not seen yet.
	 *
	 * @param location directory holding the versioned SQL
	 * @return how many migrations were applied
	 * @throws Error if a migration fails, so the server stops rather than running
	 *               against a schema it does not understand
	 */
	public static int migrate(String location) {
		if (!DatabaseConfig.DATABASE_MIGRATION_ENABLE) {
			log.warn("Skipping the schema migration: database.migration.enable is off.");
			return 0;
		}

		File directory = new File(location);
		if (!directory.isDirectory()) {
			throw new Error("Cannot migrate the schema: " + directory.getAbsolutePath() + " is not a directory.");
		}

		try {
			Flyway flyway = configure(location, DatabaseConfig.DATABASE_URL, DatabaseConfig.DATABASE_USER,
					DatabaseConfig.DATABASE_PASSWORD).load();
			logPendingWork(flyway);

			MigrateResult result = flyway.migrate();
			if (result.migrationsExecuted == 0) {
				log.info("Schema is up to date at version " + currentVersion(flyway) + ".");
			} else {
				log.info("Applied " + result.migrationsExecuted + " migration(s), schema now at version "
						+ result.targetSchemaVersion + ".");
			}
			return result.migrationsExecuted;
		} catch (FlywayException e) {
			// Carry on past this and the server would talk to a schema it disagrees
			// with, which corrupts data rather than failing.
			throw new Error("Failed to migrate the schema from " + directory.getAbsolutePath(), e);
		}
	}

	/**
	 * Builds the Flyway configuration both servers share.
	 * <p>
	 * Takes the connection details rather than reading them from the configuration,
	 * so the settings below can be asserted without a database and without loading
	 * a server config.
	 *
	 * @param location directory holding the versioned SQL
	 * @param url      JDBC url of the schema to migrate
	 * @param user     user to connect as
	 * @param password password of that user
	 * @return the configuration, not yet loaded
	 */
	static FluentConfiguration configure(String location, String url, String user, String password) {
		return Flyway.configure()
				.dataSource(url, user, password)
				.locations("filesystem:" + location)
				// Stamp an existing install instead of replaying V1 over its tables.
				// Flyway only does this when the schema already holds objects, so a
				// fresh database still runs V1 and builds itself.
				.baselineOnMigrate(true)
				.baselineVersion(BASELINE_VERSION)
				.baselineDescription("Existing schema adopted as the baseline")
				// Refuse a migration whose file changed after it was applied: the
				// database would no longer match what the file claims.
				.validateOnMigrate(true)
				// Never offer to drop the schema. Nothing here should be able to.
				.cleanDisabled(true);
	}

	/** Reports what is about to run, so a slow start explains itself. */
	private static void logPendingWork(Flyway flyway) {
		MigrationInfo[] pending = flyway.info().pending();
		if (pending.length == 0) {
			return;
		}

		StringBuilder names = new StringBuilder();
		for (MigrationInfo info : pending) {
			if (names.length() > 0) {
				names.append(", ");
			}
			names.append("V").append(info.getVersion()).append(" ").append(info.getDescription());
		}
		log.info("Pending migration(s): " + names);
	}

	/** Answers the version the database currently sits at, or none. */
	private static String currentVersion(Flyway flyway) {
		MigrationInfo current = flyway.info().current();
		return current == null ? "none" : String.valueOf(current.getVersion());
	}
}
