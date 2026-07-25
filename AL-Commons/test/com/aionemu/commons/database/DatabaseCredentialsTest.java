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

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.configs.DatabaseConfig;
import com.aionemu.commons.configuration.ConfigurableProcessor;

/**
 * Covers the credentials the connection pool is handed.
 *
 * @author Oraion
 */
class DatabaseCredentialsTest {

	@AfterEach
	void tearDown() {
		DatabaseConfig.DATABASE_PASSWORD = null;
	}

	@Test
	@DisplayName("Sends no password when the configuration carries none")
	void sendsNoPasswordWhenConfiguredBlank() {
		Properties configured = new Properties();
		configured.setProperty("database.password", "");

		ConfigurableProcessor.process(DatabaseConfig.class, configured);

		// A blank value counts as absent, so a hard-coded default would be sent in
		// its place and the server would report a password it was never given.
		assertEquals("", DatabaseConfig.DATABASE_PASSWORD);
	}

	@Test
	@DisplayName("Sends the password the configuration carries")
	void sendsTheConfiguredPassword() {
		Properties configured = new Properties();
		configured.setProperty("database.password", "s3cret");

		ConfigurableProcessor.process(DatabaseConfig.class, configured);

		assertEquals("s3cret", DatabaseConfig.DATABASE_PASSWORD);
	}
}
