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
package com.aionemu.commons.versionning;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers reading the build information out of a manifest.
 * <p>
 * Every server run through Gradle loads its classes from a directory rather than
 * an archive, so there is no manifest to read. That was reported at error level
 * with a stack trace, which read like a startup failure, and it left the four
 * fields null so the banner printed "GS Revision: null". The archive handle was
 * never closed either, which on Windows keeps the file locked.
 *
 * @author Oraion
 */
class VersionTest {

	@Test
	@DisplayName("Answers a value for every field before anything is read")
	void defaultsAreNeverNull() {
		Version version = new Version();

		assertNotNull(version.getRevision());
		assertNotNull(version.getDate());
		assertNotNull(version.getBranch());
		assertNotNull(version.getCommitTime());
	}

	@Test
	@DisplayName("Keeps the defaults when the classes come from a directory")
	void acceptsClassesLoadedFromADirectory() {
		// This test class is compiled into a directory, which is exactly the case
		// that used to be reported as an error.
		Version version = assertDoesNotThrow(() -> new Version(VersionTest.class));

		assertNotNull(version.getRevision());
		assertNotNull(version.getBranch());
		assertNotNull(version.getCommitTime());
		assertNotNull(version.getDate());
	}

	@Test
	@DisplayName("Reports a missing target instead of throwing")
	void reportsMissingTargetFile() throws IOException {
		Path missing = Files.createTempDirectory("version-test").resolve("absent.mf");

		assertDoesNotThrow(() -> new Version().transferInfo("no-such.jar", "server", missing.toFile()));
		assertTrue(Files.notExists(missing));
	}

	@Test
	@DisplayName("Leaves no handle open on an archive that cannot be read")
	void releasesTheArchiveOnFailure() throws IOException {
		Path directory = Files.createTempDirectory("version-test");
		File target = directory.resolve("manifest.mf").toFile();
		assertTrue(target.createNewFile());

		// The archive does not exist, so the copy fails; the target must stay
		// deletable, which it is not while a handle is held on Windows.
		assertDoesNotThrow(() -> new Version().transferInfo("no-such.jar", "server", target));
		assertTrue(target.delete(), "The target file is still held open.");
	}
}
