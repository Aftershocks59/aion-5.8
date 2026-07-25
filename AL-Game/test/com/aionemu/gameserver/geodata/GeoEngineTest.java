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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks how the ground is read out of a terrain grid.
 *
 * @author Oraion
 */
class GeoEngineTest {

	private static final float EPSILON = 0.0001f;

	@TempDir
	Path worlds;

	/**
	 * Writes a terrain whose corners rise by one world unit for every step along
	 * X, and are level along Y.
	 * <p>
	 * The grid runs down X in whole columns, so the corner written at index
	 * {@code i} is the one at column {@code i / stride}.
	 */
	private HeightMap rampAlongX(int cols, int rows) throws IOException {
		int stride = rows + 1;
		int cells = (cols + 1) * stride;
		ByteBuffer buffer = ByteBuffer.allocate(GeoVersion.HEADER_SIZE + 8 + cells * 4).order(ByteOrder.LITTLE_ENDIAN);
		buffer.putInt(GeoVersion.MAGIC);
		buffer.putLong(1L);
		buffer.putLong(2L);
		buffer.position(GeoVersion.HEADER_SIZE);
		buffer.putInt(cols);
		buffer.putInt(rows);
		for (int i = 0; i < cells; i++) {
			// A height of n world units is stored as n times thirty-two, which is
			// always a multiple of four and so leaves the surface bits clear.
			buffer.putShort((short) ((i / stride) * 32));
		}
		for (int i = 0; i < cells; i++) {
			buffer.put((byte) 0);
		}
		// The finest tier carries a third grid of one byte a cell.
		for (int i = 0; i < cells; i++) {
			buffer.put((byte) 0);
		}

		Path world = Files.createDirectories(worlds.resolve("ramp"));
		Path file = world.resolve(HeightMap.TIER_FILES[HeightMap.TIER_40]);
		Files.write(file, java.util.Arrays.copyOf(buffer.array(), buffer.position()));
		return HeightMap.load(world);
	}

	@Test
	@DisplayName("Indexes the last corner as the last cell of the grid")
	void lastCornerIsTheLastCell() throws IOException {
		// A world that is not square is the only one that tells the two ways of
		// composing the index apart.
		HeightMap terrain = rampAlongX(6, 10);
		assertEquals(76, terrain.getCellCount() - 1);
		assertEquals(terrain.getCellCount() - 1, terrain.cornerIndex(6, 10));
	}

	@Test
	@DisplayName("Reads a corner's own height where a position sits on it")
	void readsCornerHeights() throws IOException {
		HeightMap terrain = rampAlongX(6, 10);
		// A cell spans two world units, so column three starts at x = 6.
		assertEquals(0.0f, GeoEngine.groundZ(terrain, 0.0f, 0.0f), EPSILON);
		assertEquals(3.0f, GeoEngine.groundZ(terrain, 6.0f, 0.0f), EPSILON);
		assertEquals(3.0f, GeoEngine.groundZ(terrain, 6.0f, 14.0f), EPSILON);
	}

	@Test
	@DisplayName("Interpolates between the corners a position falls between")
	void interpolatesBetweenCorners() throws IOException {
		HeightMap terrain = rampAlongX(6, 10);
		assertEquals(1.5f, GeoEngine.groundZ(terrain, 3.0f, 0.0f), EPSILON);
		assertEquals(1.25f, GeoEngine.groundZ(terrain, 2.5f, 4.0f), EPSILON);
		// Level along Y, so moving along it changes nothing.
		assertEquals(GeoEngine.groundZ(terrain, 3.0f, 0.0f), GeoEngine.groundZ(terrain, 3.0f, 19.0f), EPSILON);
	}

	@Test
	@DisplayName("Answers the nearest corner for a position off the edge")
	void clampsOutsideTheWorld() throws IOException {
		HeightMap terrain = rampAlongX(6, 10);
		assertEquals(0.0f, GeoEngine.groundZ(terrain, -50.0f, -50.0f), EPSILON);
		assertEquals(6.0f, GeoEngine.groundZ(terrain, 1000.0f, 1000.0f), EPSILON);
	}

	@Test
	@DisplayName("Falls through to the caller's own height for a world it does not know")
	void unknownWorldFallsThrough() {
		GeoEngine engine = GeoEngine.empty();
		assertEquals(0, engine.getWorldCount());
		assertFalse(engine.knows(210010000));
		assertNull(engine.getWorld(210010000));
		assertEquals(123.5f, engine.getGroundZ(210010000, 0, 100.0f, 100.0f, 40.0f, 123.5f), EPSILON);
		assertEquals(123.5f, engine.getTerrainZ(210010000, 100.0f, 100.0f, 123.5f), EPSILON);
		assertTrue(engine.isClear(210010000, 0, 0.0f, 0.0f, 0.0f, 100.0f, 100.0f, 0.0f,
				MaterialCollision.COLUMN_MOVEMENT));
		assertNull(engine.findDoor(210010000, "AbyssDoor12"));
		assertNull(engine.getDoorNameAt(210010000, 0.0f, 0.0f, 0.0f));
		assertFalse(engine.setDoorOpen(210010000, 0, "AbyssDoor12", true));
	}

	@Test
	@DisplayName("Reads no world from a root that is not there")
	void missingRootLoadsNothing() throws IOException {
		GeoEngine engine = GeoEngine.load(worlds.resolve("absent"));
		assertEquals(0, engine.getWorldCount());
		engine.close();
	}

	@Test
	@DisplayName("Passes over a directory that is not named after a world")
	void ignoresDirectoriesThatAreNotWorlds() throws IOException {
		Files.createDirectories(worlds.resolve("readme"));
		Files.createDirectories(worlds.resolve("210010000x"));
		GeoEngine engine = GeoEngine.load(worlds);
		assertEquals(0, engine.getWorldCount());
		engine.close();
	}
}
