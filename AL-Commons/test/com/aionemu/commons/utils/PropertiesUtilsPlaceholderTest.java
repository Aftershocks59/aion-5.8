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
package com.aionemu.commons.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the placeholder substitution that keeps credentials out of tracked
 * configuration files.
 * <p>
 * A regression here would either leak a real value into a committed file or,
 * worse, silently connect with an empty password.
 *
 * @author Oraion
 */
class PropertiesUtilsPlaceholderTest {

	private static final String PROPERTY = "aion.test.placeholder";

	@AfterEach
	void clearSystemProperty() {
		System.clearProperty(PROPERTY);
	}

	@Test
	@DisplayName("Substitutes a system property when one is set")
	void resolvesFromSystemProperty() {
		System.setProperty(PROPERTY, "s3cret");
		Properties properties = propertiesOf("database.password", "${" + PROPERTY + ":fallback}");

		PropertiesUtils.resolvePlaceholders(properties);

		assertEquals("s3cret", properties.getProperty("database.password"));
	}

	@Test
	@DisplayName("Falls back to the inline default when nothing is defined")
	void resolvesToInlineDefault() {
		Properties properties = propertiesOf("database.user", "${" + PROPERTY + ":root}");

		PropertiesUtils.resolvePlaceholders(properties);

		assertEquals("root", properties.getProperty("database.user"));
	}

	@Test
	@DisplayName("Yields an empty value when the default is empty")
	void resolvesToEmptyDefault() {
		Properties properties = propertiesOf("database.password", "${" + PROPERTY + ":}");

		PropertiesUtils.resolvePlaceholders(properties);

		assertEquals("", properties.getProperty("database.password"));
	}

	@Test
	@DisplayName("Substitutes several placeholders inside one value")
	void resolvesSeveralPlaceholdersInOneValue() {
		Properties properties = propertiesOf("database.url",
				"jdbc:mariadb://${" + PROPERTY + ":localhost}:${aion.test.port:3306}/al_server_gs");

		PropertiesUtils.resolvePlaceholders(properties);

		assertEquals("jdbc:mariadb://localhost:3306/al_server_gs", properties.getProperty("database.url"));
	}

	@Test
	@DisplayName("Leaves a value without any placeholder untouched")
	void leavesPlainValueUntouched() {
		Properties properties = propertiesOf("database.driver", "org.mariadb.jdbc.Driver");

		PropertiesUtils.resolvePlaceholders(properties);

		assertEquals("org.mariadb.jdbc.Driver", properties.getProperty("database.driver"));
	}

	@Test
	@DisplayName("Keeps a dollar sign that is not a placeholder")
	void keepsLiteralDollarSign() {
		Properties properties = propertiesOf("motd", "Price is 100$ today");

		PropertiesUtils.resolvePlaceholders(properties);

		assertEquals("Price is 100$ today", properties.getProperty("motd"));
	}

	@Test
	@DisplayName("Substitutes a placeholder that carries no default")
	void resolvesPlaceholderWithoutDefault() {
		System.setProperty(PROPERTY, "value");
		Properties properties = propertiesOf("key", "${" + PROPERTY + "}");

		PropertiesUtils.resolvePlaceholders(properties);

		assertEquals("value", properties.getProperty("key"));
	}

	@Test
	@DisplayName("Empties an undefined placeholder that carries no default")
	void emptiesUndefinedPlaceholderWithoutDefault() {
		Properties properties = propertiesOf("key", "${aion.test.never.defined}");

		PropertiesUtils.resolvePlaceholders(properties);

		assertEquals("", properties.getProperty("key"));
	}

	private static Properties propertiesOf(String key, String value) {
		Properties properties = new Properties();
		properties.setProperty(key, value);
		return properties;
	}
}
