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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.configs.DatabaseConfig;

/**
 * Covers how the schema migration is set up and refused.
 * <p>
 * The settings here decide whether a live server survives a start: replaying the
 * baseline over a populated database would recreate its tables, and letting a
 * failed migration through would leave the server talking to a schema it
 * disagrees with. None of that needs a database to check.
 *
 * @author Oraion
 */
class SchemaMigratorTest {

	/** Names a database that is never contacted: nothing here opens a connection. */
	private static final String TEST_URL = "jdbc:mariadb://localhost:3306/does_not_matter";

	private boolean migrationWasEnabled;

	@BeforeEach
	void rememberConfiguration() {
		migrationWasEnabled = DatabaseConfig.DATABASE_MIGRATION_ENABLE;
		DatabaseConfig.DATABASE_MIGRATION_ENABLE = true;
	}

	@AfterEach
	void restoreConfiguration() {
		DatabaseConfig.DATABASE_MIGRATION_ENABLE = migrationWasEnabled;
	}

	@Test
	@DisplayName("Reads the migrations from the directory it is given")
	void readsTheGivenDirectory() {
		FluentConfiguration configuration = SchemaMigrator.configure("./sql/migration", TEST_URL, "user", "secret");

		assertEquals(1, configuration.getLocations().length);
		// Flyway rewrites the path with the platform separator, so compare on a
		// single form rather than tying the test to Windows or to Linux.
		String descriptor = configuration.getLocations()[0].getDescriptor().replace('\\', '/');
		assertEquals("filesystem:./sql/migration", descriptor);
	}

	@Test
	@DisplayName("Stamps an existing database rather than replaying the baseline")
	void baselinesAnExistingDatabase() {
		FluentConfiguration configuration = SchemaMigrator.configure("./sql/migration", TEST_URL, "user", "secret");

		assertTrue(configuration.isBaselineOnMigrate(), "A populated database would have its tables recreated.");
		assertEquals("1", configuration.getBaselineVersion().getVersion());
	}

	@Test
	@DisplayName("Refuses a migration whose file changed after it ran")
	void validatesAppliedMigrations() {
		assertTrue(SchemaMigrator.configure("./sql/migration", TEST_URL, "user", "secret").isValidateOnMigrate());
	}

	@Test
	@DisplayName("Never offers to drop the schema")
	void forbidsClean() {
		assertTrue(SchemaMigrator.configure("./sql/migration", TEST_URL, "user", "secret").isCleanDisabled());
	}

	@Test
	@DisplayName("Stops the server when the migration directory is missing")
	void refusesAMissingDirectory() {
		Error error = assertThrows(Error.class, () -> SchemaMigrator.migrate("./no-such-directory"));

		assertTrue(error.getMessage().contains("not a directory"), error.getMessage());
	}

	@Test
	@DisplayName("Does nothing when migration is switched off")
	void skipsWhenDisabled() throws IOException {
		DatabaseConfig.DATABASE_MIGRATION_ENABLE = false;
		// Point at a real directory so only the switch can explain the result.
		Path directory = Files.createTempDirectory("migrator-test");

		assertEquals(0, SchemaMigrator.migrate(directory.toString()));
	}

	@Test
	@DisplayName("Checks the switch before the directory")
	void skipsBeforeLookingAtTheDirectory() {
		DatabaseConfig.DATABASE_MIGRATION_ENABLE = false;

		// Turning migration off must let a server start even with nothing to read.
		assertEquals(0, SchemaMigrator.migrate("./no-such-directory"));
	}
}
