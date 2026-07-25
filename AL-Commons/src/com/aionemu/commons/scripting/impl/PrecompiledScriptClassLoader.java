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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import com.aionemu.commons.scripting.ScriptClassLoader;

/**
 * Serves script classes from an archive built ahead of time.
 * <p>
 * Mirrors what {@link javacompiler.ScriptClassLoaderImpl} exposes after a
 * compilation run, so a context loaded from an archive is indistinguishable from
 * one just compiled: same class names, same bytecode, same isolation from the
 * other contexts.
 *
 * @author Oraion
 */
public class PrecompiledScriptClassLoader extends ScriptClassLoader {

	/** Holds the bytecode of every class the archive carries, by class name. */
	private final Map<String, byte[]> byteCode;

	/** Caches the classes already defined, as the contract requires. */
	private final Map<String, Class<?>> definedClasses = new HashMap<String, Class<?>>();

	/**
	 * Reads every class an archive holds.
	 *
	 * @param archive archive produced by the build
	 * @param parent  parent class loader
	 * @throws IOException if the archive cannot be read
	 */
	public PrecompiledScriptClassLoader(File archive, ClassLoader parent) throws IOException {
		super(new URL[] { archive.toURI().toURL() }, parent);
		this.byteCode = readClasses(archive);
	}

	/**
	 * Returns how many classes the archive carried.
	 *
	 * @return class count
	 */
	public int size() {
		return byteCode.size();
	}

	@Override
	public Set<String> getCompiledClasses() {
		return Collections.unmodifiableSet(byteCode.keySet());
	}

	@Override
	public byte[] getByteCode(String className) {
		byte[] bytes = byteCode.get(className);
		return bytes == null ? null : bytes.clone();
	}

	@Override
	public synchronized Class<?> getDefinedClass(String name) {
		return definedClasses.get(name);
	}

	@Override
	public synchronized void setDefinedClass(String name, Class<?> clazz) throws IllegalArgumentException {
		if (name == null || clazz == null) {
			throw new IllegalArgumentException("Both the class name and the class are required");
		}
		definedClasses.put(name, clazz);
	}

	/**
	 * Loads every class entry of an archive into memory.
	 * <p>
	 * Reads eagerly and closes the archive: holding it open would lock the file on
	 * Windows and block the next build from replacing it.
	 *
	 * @param archive archive to read
	 * @return bytecode by class name
	 * @throws IOException if the archive cannot be read
	 */
	private static Map<String, byte[]> readClasses(File archive) throws IOException {
		Map<String, byte[]> classes = new HashMap<String, byte[]>();

		ZipFile zip = new ZipFile(archive);
		try {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
					continue;
				}

				String className = entry.getName()
						.substring(0, entry.getName().length() - ".class".length())
						.replace('/', '.');

				InputStream in = zip.getInputStream(entry);
				try {
					classes.put(className, IOUtils.toByteArray(in));
				} finally {
					IOUtils.closeQuietly(in);
				}
			}
		} finally {
			zip.close();
		}

		return classes;
	}
}
