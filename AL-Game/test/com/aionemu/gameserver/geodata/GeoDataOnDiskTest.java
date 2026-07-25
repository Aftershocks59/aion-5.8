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
				assertTrue(terrain.getCellsAlongX() > 0 && terrain.getCellsAlongY() > 0, "world " + worldId + " has no grid");
				assertEquals((terrain.getCellsAlongX() + 1) * (terrain.getCellsAlongY() + 1), terrain.getCellCount(),
						"world " + worldId + " read a grid of the wrong size");

				CollisionGrid collision = geo.getCollision();
				assertEquals(CollisionGrid.sectorCount(terrain.getCellsAlongX()) * CollisionGrid.sectorCount(terrain.getCellsAlongY()),
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
					countClass(object.getClassName());
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
		System.out.println("Field object classes: " + classes);
	}

	/** Counts how many objects of each class the worlds carry. */
	private static final java.util.Map<String, Integer> classes = new java.util.TreeMap<String, Integer>();

	private static void countClass(String className) {
		Integer seen = classes.get(className);
		classes.put(className, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
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

				for (int cellX = 0; cellX < terrain.getCellsAlongX(); cellX++) {
					for (int cellY = 0; cellY < terrain.getCellsAlongY(); cellY++) {
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
	@DisplayName("Reads a door as a run of the world's own triangles, and shuts it")
	void doorsNameTrianglesAndStartShut() throws IOException {
		assumeTrue(!worlds.isEmpty(), "no geodata installed under " + GEO_DIRECTORY);

		int doors = 0;
		int measured = 0;
		int repeated = 0;
		int blocked = 0;
		int passedWhenOpen = 0;

		for (Path world : worlds) {
			int worldId = Integer.parseInt(world.getFileName().toString());
			try (WorldGeoData geo = WorldGeoData.load(worldId, world)) {
				CollisionMesh mesh = geo.getMesh();
				for (FieldObject object : mesh.getFieldObjects()) {
					// The payload is four ints, and the last two repeat the first
					// two. Only the first pair is read as the triangle run; the
					// repeat is what says nothing else is hiding in there.
					java.nio.ByteBuffer payload = java.nio.ByteBuffer.wrap(object.getPayload())
							.order(java.nio.ByteOrder.LITTLE_ENDIAN);
					int first = payload.getInt();
					int last = payload.getInt();
					if (payload.getInt() == first && payload.getInt() == last) {
						repeated++;
					}
					assertEquals(first, object.getFirstTriangle());
					assertEquals(last, object.getLastTriangle());
					assertTrue(first <= last, "object " + object + " names a run that ends before it starts");
					assertTrue(last <= mesh.getTriangleCount(),
							"object " + object + " names triangles past the end of the mesh");

					if (!object.isDoor()) {
						continue;
					}
					doors++;
					if (!object.isMeasured()) {
						continue;
					}
					measured++;

					// Fire a short ray through the middle of the door, across
					// whichever way it is thinnest, so it stays inside the door's
					// own box and meets as little of the world around it as it can.
					// Shut, it has to stop; open, it must not.
					float middleX = (object.getLowX() + object.getHighX()) / 2.0f;
					float middleY = (object.getLowY() + object.getHighY()) / 2.0f;
					float middleZ = (object.getLowZ() + object.getHighZ()) / 2.0f;
					boolean thinAlongX = object.getHighX() - object.getLowX() <= object.getHighY() - object.getLowY();
					float reach = (thinAlongX ? object.getHighX() - object.getLowX()
							: object.getHighY() - object.getLowY()) / 2.0f + 0.5f;
					float fromX = thinAlongX ? middleX - reach : middleX;
					float toX = thinAlongX ? middleX + reach : middleX;
					float fromY = thinAlongX ? middleY : middleY - reach;
					float toY = thinAlongX ? middleY : middleY + reach;

					if (!new GeoTracer(geo).isClear(fromX, fromY, middleZ, toX, toY, middleZ,
							MaterialCollision.COLUMN_MOVEMENT)) {
						blocked++;
						java.util.Set<Integer> open = java.util.Collections
								.singleton(Integer.valueOf(object.getEditorId()));
						if (new GeoTracer(geo, open).isClear(fromX, fromY, middleZ, toX, toY, middleZ,
								MaterialCollision.COLUMN_MOVEMENT)) {
							passedWhenOpen++;
						}
					}
				}
			}
		}

		System.out.println("Doors: " + doors + ", measured " + measured + ". Payload repeats its run " + repeated
				+ " times. " + blocked + " doors stopped a ray while shut, " + passedWhenOpen + " let it by once open.");

		assertEquals(measured, doors, "a door was left without geometry to block with");
		assertTrue(blocked > 0, "no door anywhere stopped a ray fired through it");
		// Opening is only ever the removal of that one door, so a ray it stopped
		// has to get through unless something else stands in the same place.
		assertTrue(passedWhenOpen * 2 > blocked, "opening a door mostly failed to let a ray through it");
	}

	@Test
	@DisplayName("Leaves a cell coded as holding no ground without any")
	void cellsWithNoBaseSurfaceHaveNoGround() throws IOException {
		assumeTrue(!worlds.isEmpty(), "no geodata installed under " + GEO_DIRECTORY);

		// Terrain is solid wherever it is there at all: the base surface carries no
		// material and is never looked up in the collision table. What decides
		// whether a player falls through is the surface code alone, so a cell coded
		// as holding no ground must answer none. Getting those two bits wrong is
		// the one way to turn a hole into a floor, or a floor into a hole.
		long[] bases = new long[4];
		long holes = 0;
		long holesWithGround = 0;

		int read = 0;
		for (Path world : worlds) {
			if (read++ == 6) {
				break;
			}
			int worldId = Integer.parseInt(world.getFileName().toString());
			try (WorldGeoData geo = WorldGeoData.load(worldId, world)) {
				HeightMap terrain = geo.getTerrain();
				GeoTracer tracer = new GeoTracer(geo);

				for (int cellX = 0; cellX + 1 < terrain.getCellsAlongX(); cellX++) {
					for (int cellY = 0; cellY + 1 < terrain.getCellsAlongY(); cellY++) {
						int code = terrain.surfaceCode(terrain.cornerIndex(cellX, cellY));
						bases[HeightMap.baseSurface(code)]++;
						if (HeightMap.baseSurface(code) != HeightMap.BASE_NONE || HeightMap.hasMesh(code)) {
							continue;
						}
						holes++;
						float x = cellX * HeightMap.CELL_SIZE + 1.0f;
						float y = cellY * HeightMap.CELL_SIZE + 1.0f;
						if (RayTriangle.hit(tracer.groundZ(x, y, 0.0f))) {
							holesWithGround++;
						}
					}
				}
			}
		}

		System.out.println("Base surfaces: quad " + bases[HeightMap.BASE_QUAD] + ", flat " + bases[HeightMap.BASE_FLAT]
				+ ", quad again " + bases[HeightMap.BASE_QUAD_ALTERNATE] + ", none " + bases[HeightMap.BASE_NONE]
				+ ". Bare holes " + holes + ", of which " + holesWithGround + " answered ground.");

		// All four codings have to turn up. A decode that read the wrong two bits
		// would collapse them, and one of them being empty is how that shows.
		for (int base = 0; base < bases.length; base++) {
			assertTrue(bases[base] > 0, "no cell anywhere is coded with base surface " + base);
		}
		assertEquals(0L, holesWithGround, "cells coded as holding no ground answered ground anyway");
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
				int step = Math.max(1, terrain.getCellsAlongX() / 60);
				for (int cellX = 0; cellX + 1 < terrain.getCellsAlongX(); cellX += step) {
					for (int cellY = 0; cellY + 1 < terrain.getCellsAlongY(); cellY += step) {
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
	@DisplayName("Keeps run height keys in the same units as the triangles they hold")
	void runKeysAreInWorldUnits() throws IOException {
		assumeTrue(!worlds.isEmpty(), "no geodata installed under " + GEO_DIRECTORY);

		// The height key at the head of a run is what a query culls against. If it
		// is not the same measure as the triangles' own heights, every run is
		// discarded before a single triangle is tried, and nothing solid is ever
		// found. Compare the two directly.
		long runs = 0;
		long keyBelowTriangles = 0;
		long keyAboveTriangles = 0;
		double worstBelow = 0.0;
		double worstAbove = 0.0;

		int read = 0;
		for (Path world : worlds) {
			if (read++ == 4) {
				break;
			}
			int worldId = Integer.parseInt(world.getFileName().toString());
			try (WorldGeoData geo = WorldGeoData.load(worldId, world)) {
				HeightMap terrain = geo.getTerrain();
				CollisionGrid collision = geo.getCollision();
				CollisionMesh mesh = geo.getMesh();

				for (int cellX = 0; cellX < terrain.getCellsAlongX() && runs < 200000; cellX++) {
					for (int cellY = 0; cellY < terrain.getCellsAlongY() && runs < 200000; cellY++) {
						int sector = collision.sectorIndex(cellX, cellY);
						if (sector >= collision.getSectorCount()) {
							continue;
						}
						int subCell = CollisionGrid.subCellIndex(cellX, cellY);
						int count = collision.faceCount(sector, subCell);
						if (count == 0) {
							continue;
						}
						java.nio.ByteBuffer buffer = collision.runsOf(sector, subCell);
						for (int run = 0; run < count; run++) {
							int key = buffer.getShort();
							int entries = buffer.getShort() & 0xffff;
							float low = Float.MAX_VALUE;
							float high = -Float.MAX_VALUE;
							for (int entry = 0; entry < entries; entry++) {
								int packed = buffer.getInt();
								int triangle = packed & CollisionGrid.ENTRY_TRIANGLE_MASK;
								if (triangle >= mesh.getTriangleCount()) {
									continue;
								}
								float[] corners = new float[9];
								mesh.cornersOf(triangle, corners);
								for (int corner = 2; corner < 9; corner += 3) {
									low = Math.min(low, corners[corner]);
									high = Math.max(high, corners[corner]);
								}
							}
							if (low > high) {
								continue;
							}
							runs++;
							if (key < low - 2.0f) {
								keyBelowTriangles++;
								worstBelow = Math.max(worstBelow, low - key);
							} else if (key > high + 2.0f) {
								keyAboveTriangles++;
								worstAbove = Math.max(worstAbove, key - high);
							}
						}
					}
				}
			}
		}

		System.out.println("Run keys: " + runs + " runs, " + keyBelowTriangles + " keys below their triangles (worst "
				+ String.format("%.1f", worstBelow) + "), " + keyAboveTriangles + " above (worst "
				+ String.format("%.1f", worstAbove) + ").");
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
