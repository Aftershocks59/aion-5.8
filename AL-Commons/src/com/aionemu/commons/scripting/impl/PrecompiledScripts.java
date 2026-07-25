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
package com.aionemu.commons.scripting.impl;

import java.io.File;

/**
 * Locates the script archives the build produces.
 * <p>
 * Both sides derive the archive name the same way, from the context root
 * relative to the scripts directory: {@code system/handlers/quest} becomes
 * {@code cache/scripts/system-handlers-quest.jar}. Changing the rule here means
 * changing it in the Gradle build too, and the server then falls back to
 * compiling rather than misbehaving.
 *
 * @author Oraion
 */
public final class PrecompiledScripts {

	/** Holds the archives, next to the other generated caches. */
	private static final String ARCHIVE_DIRECTORY = "cache/scripts";

	/** Marks where a context root stops being a path and starts being its name. */
	private static final String SCRIPTS_MARKER = "data/scripts/";

	private PrecompiledScripts() {
	}

	/**
	 * Returns where the archive of a context root belongs.
	 *
	 * @param root directory holding the sources of one script context
	 * @return the archive location, or null when the root sits outside data/scripts
	 */
	public static File archiveFor(File root) {
		String path = root.getPath().replace('\\', '/');
		int marker = path.indexOf(SCRIPTS_MARKER);

		if (marker < 0) {
			return null;
		}

		String name = path.substring(marker + SCRIPTS_MARKER.length())
				.replaceAll("^/+|/+$", "")
				.replace('/', '-');

		return name.isEmpty() ? null : new File(ARCHIVE_DIRECTORY, name + ".jar");
	}

	/**
	 * Finds a source file changed after the given instant.
	 * <p>
	 * Returns as soon as one is found: the caller only needs to know whether the
	 * archive is stale, and walking thousands of files to completion would waste
	 * the time the archive is meant to save.
	 *
	 * @param root directory to walk
	 * @param time instant to compare against, in milliseconds
	 * @return the first source found newer than that instant, or null if none is
	 */
	public static File findSourceNewerThan(File root, long time) {
		File[] children = root.listFiles();

		if (children == null) {
			return null;
		}

		for (File child : children) {
			if (child.isDirectory()) {
				File newer = findSourceNewerThan(child, time);
				if (newer != null) {
					return newer;
				}
			} else if (child.getName().endsWith(".java") && child.lastModified() > time) {
				return child;
			}
		}

		return null;
	}
}
