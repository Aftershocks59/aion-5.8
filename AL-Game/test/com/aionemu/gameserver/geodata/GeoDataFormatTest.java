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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers reading the geodata files, on data written to match the format.
 *
 * @author Oraion
 */
class GeoDataFormatTest {

	/**
	 * Held rather than left to the framework: a mapped collision file stays
	 * locked on Windows until the collector releases it, so a directory holding
	 * one cannot be deleted on the way out of a test.
	 */
	private Path worlds;

	@BeforeEach
	void setUp() throws IOException {
		worlds = Files.createTempDirectory("geodata-test");
	}

	@AfterEach
	void tearDown() {
		deleteQuietly(worlds.toFile());
	}

	private static void deleteQuietly(java.io.File file) {
		java.io.File[] children = file.listFiles();
		if (children != null) {
			for (java.io.File child : children) {
				deleteQuietly(child);
			}
		}
		file.delete();
	}

	/** Writes the header every geodata file opens with. */
	private static void writeHeader(ByteBuffer buffer, long versionLow, long versionHigh) {
		int start = buffer.position();
		buffer.putInt(GeoVersion.MAGIC);
		buffer.putLong(versionLow);
		buffer.putLong(versionHigh);
		buffer.position(start + GeoVersion.HEADER_SIZE);
	}

	private static ByteBuffer buffer(int size) {
		return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
	}

	private Path world(String name) throws IOException {
		return Files.createDirectories(worlds.resolve(name));
	}

	private static void write(Path file, ByteBuffer buffer) throws IOException {
		Files.write(file, java.util.Arrays.copyOf(buffer.array(), buffer.position()));
	}

	/**
	 * Writes a terrain file whose cells all carry the same packed value.
	 */
	private static void writeHeightMap(Path file, int cols, int rows, int packed, boolean withExtra)
			throws IOException {
		int cells = (cols + 1) * (rows + 1);
		ByteBuffer buffer = buffer(GeoVersion.HEADER_SIZE + 8 + cells * 4);
		writeHeader(buffer, 1L, 2L);
		buffer.putInt(cols);
		buffer.putInt(rows);
		for (int i = 0; i < cells; i++) {
			buffer.putShort((short) packed);
		}
		for (int i = 0; i < cells; i++) {
			buffer.put((byte) 0);
		}
		if (withExtra) {
			for (int i = 0; i < cells; i++) {
				buffer.put((byte) 7);
			}
		}
		write(file, buffer);
	}

	@Test
	@DisplayName("Reads the finest tier and its third grid")
	void readsTheFinestTier() throws IOException {
		Path world = world("finest");
		writeHeightMap(world.resolve("HeightMap40.Dat"), 4, 4, 0x0640, true);

		HeightMap terrain = HeightMap.load(world);

		assertNotNull(terrain);
		assertEquals(HeightMap.TIER_40, terrain.getTier());
		assertEquals(4, terrain.getCols());
		assertEquals(4, terrain.getRows());
		assertEquals(5, terrain.getStride());
		assertEquals(25, terrain.getCellCount());
		assertNotNull(terrain.getExtra());
		assertEquals(7, terrain.getExtra()[0] & 0xff);
	}

	@Test
	@DisplayName("Prefers the finest tier a world ships")
	void prefersTheFinestTier() throws IOException {
		Path world = world("both");
		writeHeightMap(world.resolve("HeightMap40.Dat"), 4, 4, 0x0100, true);
		writeHeightMap(world.resolve("HeightMap32.Dat"), 8, 8, 0x0100, false);

		// One real world ships both at different sizes, so reading the wrong one
		// gives the wrong sector count and walks the collision off its end.
		assertEquals(4, HeightMap.load(world).getCols());
	}

	@Test
	@DisplayName("Falls back to the next tier when the finest is absent")
	void fallsBackToTheNextTier() throws IOException {
		Path world = world("middle");
		writeHeightMap(world.resolve("HeightMap32.Dat"), 4, 4, 0x0100, false);

		HeightMap terrain = HeightMap.load(world);

		assertEquals(1, terrain.getTier());
		assertNull(terrain.getExtra(), "only the finest tier carries a third grid");
	}

	@Test
	@DisplayName("Answers nothing for a world that ships no terrain")
	void answersNothingWithoutTerrain() throws IOException {
		assertNull(HeightMap.load(world("bare")));
	}

	@Test
	@DisplayName("Reads an elevation as thirty-seconds of a unit")
	void readsAnElevation() throws IOException {
		Path world = world("height");
		// 0x0640 = 1600 with no surface bits set, so 1600/32 = 50 units.
		writeHeightMap(world.resolve("HeightMap40.Dat"), 4, 4, 0x0640, true);

		assertEquals(50.0f, HeightMap.load(world).heightAt(0), 0.0f);
	}

	@Test
	@DisplayName("Unpacks a coarse cell into the layout the finer tiers use")
	void unpacksACoarseCell() throws IOException {
		Path world = world("coarse");
		int packed = 0xC80B;
		writeHeightMap(world.resolve("HeightMap24.Dat"), 2, 2, packed, false);

		HeightMap terrain = HeightMap.load(world);

		// The elevation moves up out of the way of the surface bits, and the four
		// bits of surface code split across the two grids.
		assertEquals(((packed >> 3) & 0x1ffc) | ((packed & 0xf) >> 2), terrain.rawCell(0));
		assertEquals((packed << 6) & 0xff, terrain.materialAt(0));
	}

	@Test
	@DisplayName("Puts a coarse cell back together the way the query side reads it")
	void roundTripsACoarseCell() throws IOException {
		Path world = world("roundtrip");
		int packed = 0xC80B;
		writeHeightMap(world.resolve("HeightMap24.Dat"), 2, 2, packed, false);

		HeightMap terrain = HeightMap.load(world);

		// Reading the surface code back out is the exact inverse of writing it in:
		// the two halves rejoin as the low nibble they started as.
		int code = ((terrain.rawCell(0) & 3) << 2) | (terrain.materialAt(0) >> 6);
		assertEquals(packed & 0xf, code);
	}

	@Test
	@DisplayName("Refuses a terrain file claiming an empty grid")
	void refusesAnEmptyGrid() throws IOException {
		Path world = world("empty");
		ByteBuffer buffer = buffer(GeoVersion.HEADER_SIZE + 8);
		writeHeader(buffer, 1L, 2L);
		buffer.putInt(0);
		buffer.putInt(0);
		write(world.resolve("HeightMap40.Dat"), buffer);

		assertThrows(IOException.class, () -> HeightMap.load(world));
	}

	@Test
	@DisplayName("Reads a file whose header carries no magic from its first byte")
	void readsAFileWithoutMagic() {
		ByteBuffer buffer = buffer(16);
		buffer.putInt(0x11223344);
		buffer.flip();

		assertSame(GeoVersion.NONE, GeoVersion.readHeader(buffer));
		assertEquals(0, buffer.position(), "the header is put back for the payload to be read from zero");
	}

	@Test
	@DisplayName("Counts the sectors spanning a world's cells")
	void countsSectors() {
		assertEquals(8, CollisionGrid.sectorCount(256));
		assertEquals(48, CollisionGrid.sectorCount(1536));
		assertEquals(4, CollisionGrid.sectorCount(128));
	}

	@Test
	@DisplayName("Maps a collision file whose sectors are all empty")
	void mapsAnEmptyCollision() throws IOException {
		Path world = world("hollow");
		writeHeightMap(world.resolve("HeightMap40.Dat"), 64, 64, 0x0100, true);

		int sectors = 2 * 2;
		ByteBuffer buffer = buffer(GeoVersion.HEADER_SIZE + sectors * 4);
		writeHeader(buffer, 1L, 2L);
		for (int i = 0; i < sectors; i++) {
			// A sector with nothing in it writes its size and stops.
			buffer.putInt(0);
		}
		write(world.resolve(CollisionGrid.FILE_NAME), buffer);

		HeightMap terrain = HeightMap.load(world);
		try (CollisionGrid collision = CollisionGrid.load(world, terrain)) {
			assertEquals(2, collision.getCols());
			assertEquals(sectors, collision.getSectorCount());
			for (int i = 0; i < sectors; i++) {
				assertTrue(collision.isEmpty(i));
				assertNull(collision.faces(i));
				assertEquals(0, collision.faceCount(i, 0));
			}
		}
	}

	@Test
	@DisplayName("Maps a collision file holding one sector of faces")
	void mapsOneSectorOfFaces() throws IOException {
		Path world = world("solid");
		writeHeightMap(world.resolve("HeightMap40.Dat"), 32, 32, 0x0100, true);

		int meshSize = 8;
		ByteBuffer buffer = buffer(GeoVersion.HEADER_SIZE + 4 + CollisionGrid.INDEX_SIZE + meshSize);
		writeHeader(buffer, 1L, 2L);
		buffer.putInt(meshSize);
		buffer.putShort((short) 1);
		for (int i = 1; i < CollisionGrid.SUB_CELLS; i++) {
			buffer.putShort((short) 0);
		}
		buffer.putShort((short) 0x0640);
		buffer.putShort((short) 1);
		buffer.putShort((short) 11);
		buffer.putShort((short) 22);
		write(world.resolve(CollisionGrid.FILE_NAME), buffer);

		HeightMap terrain = HeightMap.load(world);
		try (CollisionGrid collision = CollisionGrid.load(world, terrain)) {
			assertEquals(1, collision.getSectorCount());
			assertEquals(meshSize, collision.getMeshSize(0));
			assertEquals(1, collision.faceCount(0, 0));
			assertEquals(0, collision.faceCount(0, 1));
			assertEquals(meshSize, collision.faces(0).remaining());
		}
	}

	@Test
	@DisplayName("Refuses a collision file whose sector runs off the end")
	void refusesAnOverlongSector() throws IOException {
		Path world = world("truncated");
		writeHeightMap(world.resolve("HeightMap40.Dat"), 32, 32, 0x0100, true);

		ByteBuffer buffer = buffer(GeoVersion.HEADER_SIZE + 4 + CollisionGrid.INDEX_SIZE);
		writeHeader(buffer, 1L, 2L);
		buffer.putInt(4096);
		buffer.position(buffer.capacity());
		write(world.resolve(CollisionGrid.FILE_NAME), buffer);

		HeightMap terrain = HeightMap.load(world);
		assertThrows(IOException.class, () -> CollisionGrid.load(world, terrain));
	}

	/** Writes a mesh of four vertices and two triangles, plus one door. */
	private static void writeMesh(Path file) throws IOException {
		ByteBuffer buffer = buffer(512);
		writeHeader(buffer, 1L, 2L);

		buffer.putInt(4);
		buffer.putFloat(10.0f).putFloat(20.0f).putFloat(30.0f);
		buffer.putFloat(11.0f).putFloat(20.0f).putFloat(30.0f);
		buffer.putFloat(11.0f).putFloat(21.0f).putFloat(30.0f);
		buffer.putFloat(10.0f).putFloat(21.0f).putFloat(30.0f);

		buffer.putInt(2);
		// The first triangle starts at vertex zero and reaches the next two.
		buffer.putInt(0).putInt((6 << 16) | 3);
		// The second starts at vertex two and reaches back to vertices three and zero.
		buffer.putInt(6).putInt(((-6 & 0xffff) << 16) | 3);
		buffer.put((byte) 0).put((byte) 4);

		byte[] className = "Door".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		byte[] name = "AbyssDoor12".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
		buffer.putInt(1);
		buffer.putInt(42);
		buffer.put(className).put((byte) 0);
		buffer.put(name).put((byte) 0);
		buffer.putInt(FieldObject.PAYLOAD_SIZE);
		buffer.put(new byte[FieldObject.PAYLOAD_SIZE]);

		write(file, buffer);
	}

	@Test
	@DisplayName("Reads the vertices, the triangles and the shape-changing objects")
	void readsTheMesh() throws IOException {
		Path world = world("mesh");
		writeMesh(world.resolve(CollisionMesh.FILE_NAME));

		CollisionMesh mesh = CollisionMesh.load(world);

		assertEquals(4, mesh.getVertexCount());
		assertEquals(2, mesh.getTriangleCount());
		assertEquals(0, mesh.materialOf(0));
		assertEquals(4, mesh.materialOf(1));
		assertEquals(1, mesh.getFieldObjects().size());

		FieldObject door = mesh.getFieldObjects().get(0);
		assertEquals(42, door.getEditorId());
		assertEquals("Door", door.getClassName());
		assertEquals("AbyssDoor12", door.getName());
		assertEquals(FieldObject.PAYLOAD_SIZE, door.getPayload().length);
	}

	@Test
	@DisplayName("Reaches a triangle's other two corners by offset from its first")
	void readsATriangleByOffset() throws IOException {
		Path world = world("corners");
		writeMesh(world.resolve(CollisionMesh.FILE_NAME));

		CollisionMesh mesh = CollisionMesh.load(world);
		float[] corners = new float[9];
		mesh.cornersOf(0, corners);

		assertArrayEquals(new float[] { 10f, 20f, 30f, 11f, 20f, 30f, 11f, 21f, 30f }, corners, 0f);
	}

	@Test
	@DisplayName("Reaches backwards when a triangle's corners come before its first")
	void readsATriangleBackwards() throws IOException {
		Path world = world("backwards");
		writeMesh(world.resolve(CollisionMesh.FILE_NAME));

		CollisionMesh mesh = CollisionMesh.load(world);
		float[] corners = new float[9];
		mesh.cornersOf(1, corners);

		// The two offsets are signed, so a triangle can name a corner it has
		// already passed.
		assertArrayEquals(new float[] { 11f, 21f, 30f, 10f, 21f, 30f, 10f, 20f, 30f }, corners, 0f);
	}

	@Test
	@DisplayName("Refuses a mesh file with bytes left over")
	void refusesTrailingBytes() throws IOException {
		Path world = world("trailing");
		ByteBuffer buffer = buffer(256);
		writeHeader(buffer, 1L, 2L);
		buffer.putInt(1);
		buffer.put(new byte[CollisionMesh.VERTEX_STRIDE]);
		buffer.putInt(0);
		buffer.putInt(0);
		buffer.putInt(0xdeadbeef);
		write(world.resolve(CollisionMesh.FILE_NAME), buffer);

		assertThrows(IOException.class, () -> CollisionMesh.load(world));
	}

	@Test
	@DisplayName("Refuses a mesh claiming more triangles than it holds")
	void refusesAnOverlongTriangleCount() throws IOException {
		Path world = world("overlong");
		ByteBuffer buffer = buffer(256);
		writeHeader(buffer, 1L, 2L);
		buffer.putInt(1);
		buffer.put(new byte[CollisionMesh.VERTEX_STRIDE]);
		buffer.putInt(1000000);
		write(world.resolve(CollisionMesh.FILE_NAME), buffer);

		assertThrows(IOException.class, () -> CollisionMesh.load(world));
	}
}
