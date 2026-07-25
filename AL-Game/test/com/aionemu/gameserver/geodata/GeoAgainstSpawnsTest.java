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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Measures the ground the geodata answers against where the game actually puts
 * things.
 * <p>
 * Every spawn in the static data is a position the live game stood something
 * at, so the ground under it is known independently of anything read here. That
 * makes it the one check that can tell a self-consistent reading of the files
 * from a correct one: a grid read with its two axes the wrong way round agrees
 * with itself perfectly and with the world not at all.
 *
 * @author Oraion
 */
class GeoAgainstSpawnsTest {

	private static final Path GEO_DIRECTORY = Paths.get("data", "geo");
	private static final Path SPAWN_DIRECTORY = Paths.get("data", "static_data", "spawns");

	private static final Pattern MAP = Pattern.compile("map_id=\"(\\d+)\"");
	private static final Pattern SPOT = Pattern
			.compile("<spot\\s+x=\"([-\\d.eE]+)\"\\s+y=\"([-\\d.eE]+)\"\\s+z=\"([-\\d.eE]+)\"");

	/** How far from a spawn's own height the ground may be and still count as under it. */
	private static final float TOLERANCE = 1.0f;

	/** One position the game stood something at. */
	private static final class Spot {

		final float x;
		final float y;
		final float z;

		Spot(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private static Map<Integer, List<Spot>> readSpawns() throws IOException {
		Map<Integer, List<Spot>> byWorld = new HashMap<Integer, List<Spot>>();
		if (!Files.isDirectory(SPAWN_DIRECTORY)) {
			return byWorld;
		}
		try (Stream<Path> files = Files.walk(SPAWN_DIRECTORY)) {
			for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
				if (!file.getFileName().toString().endsWith(".xml")) {
					continue;
				}
				String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				// A file holds one or more maps, each followed by its own spots.
				for (String block : text.split("<spawn_map")) {
					Matcher map = MAP.matcher(block);
					if (!map.find()) {
						continue;
					}
					Integer worldId = Integer.valueOf(map.group(1));
					List<Spot> spots = byWorld.get(worldId);
					if (spots == null) {
						spots = new ArrayList<Spot>();
						byWorld.put(worldId, spots);
					}
					Matcher spot = SPOT.matcher(block);
					while (spot.find()) {
						spots.add(new Spot(Float.parseFloat(spot.group(1)), Float.parseFloat(spot.group(2)),
								Float.parseFloat(spot.group(3))));
					}
				}
			}
		}
		return byWorld;
	}

	@Test
	@DisplayName("Puts the ground where the game stands its spawns")
	void groundMatchesSpawns() throws IOException {
		assumeTrue(Files.isDirectory(GEO_DIRECTORY), "no geodata installed under " + GEO_DIRECTORY);
		Map<Integer, List<Spot>> spawns = readSpawns();
		assumeTrue(!spawns.isEmpty(), "no spawns found under " + SPAWN_DIRECTORY);

		int worldsRead = 0;
		int asked = 0;
		int nearStraight = 0;
		int nearSwapped = 0;
		List<Double> straightGaps = new ArrayList<Double>();

		List<Integer> worldIds = new ArrayList<Integer>(spawns.keySet());
		Collections.sort(worldIds);
		for (Integer worldId : worldIds) {
			Path directory = GEO_DIRECTORY.resolve(worldId.toString());
			if (!Files.isDirectory(directory) || worldsRead == 12) {
				continue;
			}
			List<Spot> spots = spawns.get(worldId);
			if (spots.size() < 20) {
				continue;
			}
			worldsRead++;

			try (WorldGeoData geo = WorldGeoData.load(worldId.intValue(), directory)) {
				GeoTracer tracer = new GeoTracer(geo);
				for (Spot spot : spots) {
					asked++;
					float straight = tracer.groundZ(spot.x, spot.y, spot.z);
					float swapped = tracer.groundZ(spot.y, spot.x, spot.z);
					if (RayTriangle.hit(straight) && Math.abs(straight - spot.z) <= TOLERANCE) {
						nearStraight++;
						straightGaps.add(Double.valueOf(Math.abs(straight - spot.z)));
					}
					if (RayTriangle.hit(swapped) && Math.abs(swapped - spot.z) <= TOLERANCE) {
						nearSwapped++;
					}
				}
			}
		}

		double median = 0.0;
		if (!straightGaps.isEmpty()) {
			Collections.sort(straightGaps);
			median = straightGaps.get(straightGaps.size() / 2).doubleValue();
		}

		System.out.println("Spawns checked: " + asked + " over " + worldsRead + " worlds. Ground within "
				+ TOLERANCE + " of the spawn: " + nearStraight + " as read, " + nearSwapped + " with X and Y swapped."
				+ " Median gap when it agrees: " + String.format("%.3f", median));

		assumeTrue(asked > 0, "no spawn fell in a world that has geodata");
		assertTrue(nearStraight > nearSwapped,
				"the ground agrees with the game's own spawns better with the two axes swapped, which means they are");
	}
}
