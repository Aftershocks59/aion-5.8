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
package com.aionemu.loginserver;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the login server migrations before a boot has to.
 * <p>
 * Flyway decides what to run from the file name alone. A name it does not
 * recognise is skipped in silence, and two files claiming the same version stop
 * the server at startup.
 *
 * @author Oraion
 */
class MigrationFilesTest {

	/** Matches the name Flyway expects: a version, two underscores, a description. */
	private static final Pattern MIGRATION = Pattern.compile("^V(\\d+(?:\\.\\d+)*)__(\\S.*)\\.sql$");

	private static final Path DIRECTORY = Paths.get("sql", "migration");

	private static List<Path> migrations() throws IOException {
		try (Stream<Path> files = Files.list(DIRECTORY)) {
			return files.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
		}
	}

	@Test
	@DisplayName("Holds the migration directory the server reads")
	void directoryExists() {
		assertTrue(Files.isDirectory(DIRECTORY),
				"Expected " + DIRECTORY.toAbsolutePath() + ", which the server migrates from at startup.");
	}

	@Test
	@DisplayName("Names every migration the way Flyway expects")
	void everyFileIsNamedForFlyway() throws IOException {
		List<String> rejected = new ArrayList<String>();
		for (Path file : migrations()) {
			if (!MIGRATION.matcher(file.getFileName().toString()).matches()) {
				rejected.add(file.getFileName().toString());
			}
		}

		assertTrue(rejected.isEmpty(), "Flyway would skip these without a word: " + rejected);
	}

	@Test
	@DisplayName("Claims each version once")
	void versionsAreUnique() throws IOException {
		Set<String> seen = new HashSet<String>();
		List<String> duplicates = new ArrayList<String>();

		for (Path file : migrations()) {
			Matcher matcher = MIGRATION.matcher(file.getFileName().toString());
			if (matcher.matches() && !seen.add(matcher.group(1))) {
				duplicates.add(file.getFileName().toString());
			}
		}

		assertTrue(duplicates.isEmpty(), "Two migrations claim the same version: " + duplicates);
	}

	@Test
	@DisplayName("Starts at the baseline the migrator stamps")
	void baselineIsVersionOne() throws IOException {
		List<String> names = new ArrayList<String>();
		for (Path file : migrations()) {
			names.add(file.getFileName().toString());
		}

		assertTrue(names.stream().anyMatch(name -> name.startsWith("V1__")), "No V1 baseline among " + names);
	}

	@Test
	@DisplayName("Leaves no migration empty")
	void noMigrationIsEmpty() throws IOException {
		for (Path file : migrations()) {
			assertTrue(file.toFile().length() > 0, file.getFileName() + " is empty.");
		}
	}
}
