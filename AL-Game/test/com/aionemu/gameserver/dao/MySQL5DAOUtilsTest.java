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
package com.aionemu.gameserver.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the database compatibility check every DAO delegates to.
 * <p>
 * The original demanded the literal product name "MySQL" with major version
 * exactly 5. Against MariaDB, or against MySQL 8, no DAO declared itself usable,
 * the registry loaded nothing, and the login server aborted with
 * DAONotFoundException before opening a port.
 *
 * @author Oraion
 */
class MySQL5DAOUtilsTest {

	@Test
	@DisplayName("Accepts MariaDB, whose driver reports that product name")
	void acceptsMariaDb() {
		assertTrue(MySQL5DAOUtils.supports("MariaDB", 11, 4));
	}

	@Test
	@DisplayName("Accepts MySQL 5, the historically supported version")
	void acceptsMySql5() {
		assertTrue(MySQL5DAOUtils.supports("MySQL", 5, 7));
	}

	@Test
	@DisplayName("Accepts MySQL 8, which the exact version check used to reject")
	void acceptsMySql8() {
		assertTrue(MySQL5DAOUtils.supports("MySQL", 8, 0));
	}

	@Test
	@DisplayName("Ignores the case of the reported product name")
	void ignoresProductNameCase() {
		assertTrue(MySQL5DAOUtils.supports("mariadb", 11, 4));
		assertTrue(MySQL5DAOUtils.supports("mysql", 8, 0));
	}

	@Test
	@DisplayName("Rejects a version older than 5")
	void rejectsOlderMajorVersion() {
		assertFalse(MySQL5DAOUtils.supports("MySQL", 4, 1));
	}

	@Test
	@DisplayName("Rejects an unrelated database engine")
	void rejectsOtherEngine() {
		assertFalse(MySQL5DAOUtils.supports("PostgreSQL", 16, 0));
	}

	@Test
	@DisplayName("Rejects a null product name instead of failing")
	void rejectsNullProductName() {
		assertFalse(MySQL5DAOUtils.supports(null, 11, 4));
	}
}
