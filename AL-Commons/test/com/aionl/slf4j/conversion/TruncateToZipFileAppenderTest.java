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
package com.aionl.slf4j.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers reading the time an old log was opened before archiving it.
 * <p>
 * A leftover log whose first line did not carry that time threw out of the
 * appender, and logging is set up before anything else, so the server would not
 * start at all until the file was deleted by hand.
 *
 * @author Oraion
 */
class TruncateToZipFileAppenderTest {

	@TempDir
	Path directory;

	private File logHolding(String content) throws IOException {
		Path file = directory.resolve("server.log");
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
		return file.toFile();
	}

	@Test
	@DisplayName("Reads the time from behind the form feed")
	void readsTheMarkedTime() throws IOException {
		assertEquals("2026.07.25 10-30-00",
				TruncateToZipFileAppender.readStartTime(logHolding("Log start\f2026.07.25 10-30-00\nnext line\n")));
	}

	@Test
	@DisplayName("Answers no time for a log that was never marked")
	void unmarkedLogHasNoTime() throws IOException {
		assertEquals("", TruncateToZipFileAppender.readStartTime(logHolding("just a line with no marker\n")));
	}

	@Test
	@DisplayName("Answers no time for an empty log")
	void emptyLogHasNoTime() throws IOException {
		assertEquals("", TruncateToZipFileAppender.readStartTime(logHolding("")));
	}

	@Test
	@DisplayName("Answers no time for a log that is not there")
	void missingLogHasNoTime() {
		assertEquals("", TruncateToZipFileAppender.readStartTime(directory.resolve("absent.log").toFile()));
	}
}
