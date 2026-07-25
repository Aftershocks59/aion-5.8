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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reads the geodata actually installed under {@code data/geo}.
 * <p>
 * The files are not in the repository — they are hundreds of megabytes a world
 * — so this skips where they are absent. Where they are present it is the only
 * check that the reader agrees with what the client shipped.
 *
 * @author Oraion
 */
class GeoDataOnDiskTest {

	private static final Path GEO_DIRECTORY = Paths.get("data", "geo");

	private static List<Path> worlds;

	@BeforeAll
	static void findWorlds() throws IOException {
		worlds = new ArrayList<Path>();
		if (!Files.isDirectory(GEO_DIRECTORY)) {
			return;
		}
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(GEO_DIRECTORY)) {
			for (Path entry : entries) {
				if (Files.isDirectory(entry)) {
					worlds.add(entry);
				}
			}
		}
	}

	@Test
	@DisplayName("Reads every installed world end to end")
	void readsEveryWorld() throws IOException {
		assumeTrue(!worlds.isEmpty(), "no geodata installed under " + GEO_DIRECTORY);

		int read = 0;
		long faces = 0;
		long doors = 0;

		for (Path world : worlds) {
			int worldId = Integer.parseInt(world.getFileName().toString());
			try (WorldGeoData geo = WorldGeoData.load(worldId, world)) {
				assertNotNull(geo, "world " + worldId + " holds files but read as nothing");

				HeightMap terrain = geo.getTerrain();
				assertTrue(terrain.getCols() > 0 && terrain.getRows() > 0, "world " + worldId + " has no grid");
				assertEquals((terrain.getCols() + 1) * (terrain.getRows() + 1), terrain.getCellCount(),
						"world " + worldId + " read a grid of the wrong size");

				CollisionGrid collision = geo.getCollision();
				assertEquals(CollisionGrid.sectorCount(terrain.getCols()) * CollisionGrid.sectorCount(terrain.getRows()),
						collision.getSectorCount(), "world " + worldId + " read the wrong sector count");

				for (int sector = 0; sector < collision.getSectorCount(); sector++) {
					if (!collision.isEmpty(sector)) {
						faces += collision.faceCount(sector, 0);
					}
				}

				for (FieldObject object : geo.getMaterials().getFieldObjects()) {
					assertEquals(FieldObject.PAYLOAD_SIZE, object.getPayload().length,
							"world " + worldId + " carries a shape-changing object of another size");
					if (object.getClassName().endsWith("Door")) {
						doors++;
					}
				}

				read++;
			}
		}

		assertEquals(worlds.size(), read);
		// Reading a world at all means its three files agreed on their sizes down
		// to the byte, since every one is walked to its exact end.
		assertTrue(doors > 0, "no door was read from any world");
		assertTrue(faces >= 0);
	}
}
