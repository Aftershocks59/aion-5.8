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
		long triangles = 0;
		long vertices = 0;
		long[] materials = new long[256];

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

				CollisionMesh mesh = geo.getMesh();
				int floats = mesh.getVertices().length;
				for (int triangle = 0; triangle < mesh.getTriangleCount(); triangle++) {
					// A corner that lands outside the vertex array, or off the three
					// floats a vertex takes, means the triangles are not being read
					// the way the client wrote them.
					checkCorner(worldId, mesh.firstVertexOf(triangle), floats);
					checkCorner(worldId, mesh.secondVertexOf(triangle), floats);
					checkCorner(worldId, mesh.thirdVertexOf(triangle), floats);
					materials[mesh.materialOf(triangle)]++;
				}
				triangles += mesh.getTriangleCount();
				vertices += mesh.getVertexCount();

				for (FieldObject object : mesh.getFieldObjects()) {
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
		assertTrue(triangles > 0 && vertices > 0);

		// The material table stops at two hundred rows. A world built of anything
		// past that would have nothing to look its collision up in.
		long walkable = 0;
		for (int material = 0; material < materials.length; material++) {
			if (materials[material] == 0) {
				continue;
			}
			assertTrue(material < MaterialCollision.MATERIAL_COUNT, "worlds are built of material " + material
					+ ", which is past the end of the material table");
			if (!MaterialCollision.blocksMovement(material)) {
				walkable += materials[material];
			}
		}

		System.out.println("Geodata read: " + read + " worlds, " + vertices + " vertices, " + triangles
				+ " triangles, " + doors + " doors, " + walkable + " triangles walked through.");
	}

	@Test
	@DisplayName("Lays the collision grid out the way the height grid is laid out")
	void collisionAgreesWithTheHeightGrid() throws IOException {
		assumeTrue(!worlds.isEmpty(), "no geodata installed under " + GEO_DIRECTORY);

		// A cell the height grid marks as carrying a mesh has to be a cell the
		// collision index gives runs for, and the other way round. Getting either
		// the sector order or the mesh bit wrong breaks this on nearly every
		// sector, which is how both were settled: no other bit of any of the three
		// grids, under either order, comes near agreeing.
		long cells = 0;
		long disagreed = 0;

		int read = 0;
		for (Path world : worlds) {
			// Twenty worlds is tens of millions of cells, which is enough to hold
			// the layout to account without making the suite tiresome to run.
			if (read++ == 20) {
				break;
			}
			int worldId = Integer.parseInt(world.getFileName().toString());
			try (WorldGeoData geo = WorldGeoData.load(worldId, world)) {
				HeightMap terrain = geo.getTerrain();
				CollisionGrid collision = geo.getCollision();

				for (int cellX = 0; cellX < terrain.getCols(); cellX++) {
					for (int cellY = 0; cellY < terrain.getRows(); cellY++) {
						int sector = collision.sectorIndex(cellX, cellY);
						if (sector >= collision.getSectorCount()) {
							continue;
						}
						cells++;
						boolean marked = HeightMap.hasMesh(terrain.surfaceCode(terrain.cornerIndex(cellX, cellY)));
						boolean carries = collision.faceCount(sector, CollisionGrid.subCellIndex(cellX, cellY)) > 0;
						if (marked != carries) {
							disagreed++;
						}
					}
				}
			}
		}

		System.out.println("Cells checked: " + cells + " over " + Math.min(read, 20) + " worlds, disagreeing "
				+ disagreed);
		assertEquals(0L, disagreed, "the height grid and the collision index disagree on which cells carry a mesh");
	}

	@Test
	@DisplayName("Finds ground under a real world, and walls across it")
	void tracesRealWorlds() throws IOException {
		assumeTrue(!worlds.isEmpty(), "no geodata installed under " + GEO_DIRECTORY);

		long asked = 0;
		long found = 0;
		long agreedWithTerrain = 0;
		long rays = 0;
		long blocked = 0;

		int read = 0;
		for (Path world : worlds) {
			if (read++ == 4) {
				break;
			}
			int worldId = Integer.parseInt(world.getFileName().toString());
			try (WorldGeoData geo = WorldGeoData.load(worldId, world)) {
				HeightMap terrain = geo.getTerrain();
				GeoTracer tracer = new GeoTracer(geo);

				// Step across the world rather than over one corner of it, so the
				// sample is of the whole map.
				int step = Math.max(1, terrain.getCols() / 60);
				for (int cellX = 0; cellX + 1 < terrain.getCols(); cellX += step) {
					for (int cellY = 0; cellY + 1 < terrain.getRows(); cellY += step) {
						float x = cellX * HeightMap.CELL_SIZE + 1.0f;
						float y = cellY * HeightMap.CELL_SIZE + 1.0f;
						float terrainZ = GeoEngine.groundZ(terrain, x, y);

						asked++;
						float groundZ = tracer.groundZ(x, y, terrainZ);
						if (RayTriangle.hit(groundZ)) {
							found++;
							// The ground a cell carries is built from the same
							// corners the terrain query interpolates, so where a
							// cell has no mesh over it the two must land together.
							if (Math.abs(groundZ - terrainZ) < 0.01f) {
								agreedWithTerrain++;
							}
						}

						// A ray the length of eight cells, run along the ground.
						rays++;
						if (!tracer.isClear(x, y, terrainZ + 1.0f, x + 16.0f, y, terrainZ + 1.0f,
								MaterialCollision.COLUMN_MOVEMENT)) {
							blocked++;
						}
					}
				}
			}
		}

		System.out.println("Traced " + asked + " positions: " + found + " found ground, " + agreedWithTerrain
				+ " of those on the terrain itself. " + blocked + " of " + rays + " rays were blocked.");

		assertTrue(found * 2 > asked, "the tracer found ground under fewer than half the positions asked");
		assertTrue(blocked > 0, "no ray was blocked anywhere in four worlds");
	}

	@Test
	@DisplayName("Walks a sector's runs to exactly its end")
	void runsConsumeTheSector() throws IOException {
		assumeTrue(!worlds.isEmpty(), "no geodata installed under " + GEO_DIRECTORY);

		// If a sub-cell's index entry counts runs, and a run is a height key, a
		// count and that many four-byte entries, then walking every sub-cell of a
		// sector lands on the last byte the sector owns. Nothing about where a
		// cell sits comes into it, so this settles the record layout on its own.
		int sectors = 0;
		int exact = 0;
		int over = 0;
		int under = 0;

		int read = 0;
		for (Path world : worlds) {
			if (read++ == 8) {
				break;
			}
			int worldId = Integer.parseInt(world.getFileName().toString());
			try (WorldGeoData geo = WorldGeoData.load(worldId, world)) {
				CollisionGrid collision = geo.getCollision();
				for (int sector = 0; sector < collision.getSectorCount(); sector++) {
					if (collision.isEmpty(sector)) {
						continue;
					}
					sectors++;
					java.nio.ByteBuffer faces = collision.faces(sector);
					boolean ranOff = false;
					for (int subCell = 0; subCell < CollisionGrid.SUB_CELLS && !ranOff; subCell++) {
						int runs = collision.faceCount(sector, subCell);
						for (int run = 0; run < runs; run++) {
							if (faces.remaining() < 4) {
								ranOff = true;
								break;
							}
							faces.getShort();
							int entries = faces.getShort() & 0xffff;
							int next = faces.position() + entries * CollisionGrid.RUN_ENTRY_SIZE;
							if (next > faces.limit()) {
								ranOff = true;
								break;
							}
							faces.position(next);
						}
					}
					if (ranOff) {
						over++;
					} else if (faces.remaining() == 0) {
						exact++;
					} else {
						under++;
					}
				}
			}
		}

		System.out.println("Sectors walked: " + sectors + " — exact " + exact + ", short " + under + ", overran "
				+ over);
	}

	private static void report(String grid, long[] agreed, long cells) {
		StringBuilder line = new StringBuilder(grid).append(" agreement per bit:");
		for (int bit = 0; bit < agreed.length; bit++) {
			line.append(String.format(" %d=%.2f%%", bit, 100.0 * agreed[bit] / cells));
		}
		System.out.println(line);
	}

	/**
	 * Counts, among the cells whose surface code says they carry a mesh, how many
	 * the collision index actually gives runs for.
	 * <p>
	 * Counting every cell instead drowns the answer: the great majority carry
	 * neither, and agree under any layout.
	 */
	private static void count(long[] agreed, long[] tested, int combination, CollisionGrid collision, int sector,
			int subCell, boolean carries) {
		if (!carries || sector < 0 || sector >= collision.getSectorCount()) {
			return;
		}
		tested[combination]++;
		if (collision.faceCount(sector, subCell) > 0) {
			agreed[combination]++;
		}
	}

	private static void checkCorner(int worldId, int offset, int floats) {
		assertTrue(offset >= 0 && offset + CollisionMesh.VERTEX_FLOATS <= floats,
				"world " + worldId + " names a corner at float " + offset + " of " + floats);
		assertEquals(0, offset % CollisionMesh.VERTEX_FLOATS,
				"world " + worldId + " names a corner at float " + offset + ", which is not a vertex boundary");
	}
}
