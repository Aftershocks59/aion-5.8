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
package com.aionemu.gameserver.geodata;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Answers questions about the ground, from the geodata the client ships.
 * <p>
 * Worlds live one directory per world id under the geodata root, and are read
 * when the engine is loaded. A world with no directory is not an error: it
 * simply has no geodata, and every query about it falls back to the answer it
 * would give with geodata switched off.
 *
 * @author Oraion
 */
public final class GeoEngine implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(GeoEngine.class);

	private final Map<Integer, WorldGeoData> worlds;

	private GeoEngine(Map<Integer, WorldGeoData> worlds) {
		this.worlds = Collections.unmodifiableMap(worlds);
	}

	/** Answers an engine that knows no world, which every query falls through. */
	public static GeoEngine empty() {
		return new GeoEngine(new HashMap<Integer, WorldGeoData>());
	}

	/**
	 * Reads every world under a geodata root.
	 * <p>
	 * A world that fails to read is logged and left out rather than stopping the
	 * rest: one bad export should not cost the server every other world.
	 *
	 * @param root the geodata directory, holding one directory per world id
	 * @return the engine, never null
	 * @throws IOException if the root itself cannot be listed
	 */
	public static GeoEngine load(Path root) throws IOException {
		Map<Integer, WorldGeoData> worlds = new HashMap<Integer, WorldGeoData>();
		if (!Files.isDirectory(root)) {
			log.warn("Geodata is switched on but " + root + " does not exist. No world will collide.");
			return new GeoEngine(worlds);
		}

		long started = System.nanoTime();
		int skipped = 0;
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
			for (Path entry : entries) {
				if (!Files.isDirectory(entry)) {
					continue;
				}
				Integer worldId = worldIdOf(entry);
				if (worldId == null) {
					continue;
				}
				try {
					WorldGeoData world = WorldGeoData.load(worldId.intValue(), entry);
					if (world != null) {
						worlds.put(worldId, world);
					}
				} catch (IOException e) {
					log.warn("Skipped the geodata of world " + worldId + ": " + e.getMessage());
					skipped++;
				}
			}
		}

		long millis = (System.nanoTime() - started) / 1000000L;
		log.info("Loaded the geodata of " + worlds.size() + " worlds in " + millis + " ms"
				+ (skipped == 0 ? "." : ", skipping " + skipped + " that would not read."));
		return new GeoEngine(worlds);
	}

	/** Reads a world id from a directory name, or answers null if it is not one. */
	private static Integer worldIdOf(Path directory) {
		String name = directory.getFileName().toString();
		for (int i = 0; i < name.length(); i++) {
			if (name.charAt(i) < '0' || name.charAt(i) > '9') {
				return null;
			}
		}
		try {
			return Integer.valueOf(name);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Answers how many worlds carry geodata. */
	public int getWorldCount() {
		return worlds.size();
	}

	/** Answers whether a world carries geodata. */
	public boolean knows(int worldId) {
		return worlds.containsKey(Integer.valueOf(worldId));
	}

	/** Answers a world's geodata, or null where it has none. */
	public WorldGeoData getWorld(int worldId) {
		return worlds.get(Integer.valueOf(worldId));
	}

	/**
	 * Answers the height of the ground under a position.
	 * <p>
	 * The grid stores a height at every cell corner, so a position between four
	 * of them is interpolated across the two it lies between on each axis. A
	 * position off the edge of the world is answered with its nearest corner.
	 *
	 * @param worldId  which world
	 * @param x        world position along X
	 * @param y        world position along Y
	 * @param fallback what to answer where the world has no geodata
	 * @return the ground height in world units
	 */
	public float getGroundZ(int worldId, float x, float y, float fallback) {
		WorldGeoData world = worlds.get(Integer.valueOf(worldId));
		if (world == null) {
			return fallback;
		}
		return groundZ(world.getTerrain(), x, y);
	}

	/** Interpolates the ground height across the four corners a position falls between. */
	static float groundZ(HeightMap terrain, float x, float y) {
		float cellX = x / HeightMap.CELL_SIZE;
		float cellY = y / HeightMap.CELL_SIZE;

		int x0 = clamp((int) Math.floor(cellX), terrain.getCols() - 1);
		int y0 = clamp((int) Math.floor(cellY), terrain.getRows() - 1);
		float alongX = clampFraction(cellX - x0);
		float alongY = clampFraction(cellY - y0);

		float lowX = terrain.heightAt(terrain.cornerIndex(x0, y0));
		float lowXHighY = terrain.heightAt(terrain.cornerIndex(x0, y0 + 1));
		float highX = terrain.heightAt(terrain.cornerIndex(x0 + 1, y0));
		float highXHighY = terrain.heightAt(terrain.cornerIndex(x0 + 1, y0 + 1));

		float low = lowX + (lowXHighY - lowX) * alongY;
		float high = highX + (highXHighY - highX) * alongY;
		return low + (high - low) * alongX;
	}

	private static int clamp(int value, int highest) {
		if (value < 0) {
			return 0;
		}
		return value > highest ? highest : value;
	}

	private static float clampFraction(float value) {
		if (value < 0.0f) {
			return 0.0f;
		}
		return value > 1.0f ? 1.0f : value;
	}

	@Override
	public void close() {
		for (WorldGeoData world : worlds.values()) {
			try {
				world.close();
			} catch (IOException e) {
				log.warn("Failed to release the geodata of world " + world.getWorldId() + ": " + e.getMessage());
			}
		}
	}
}
