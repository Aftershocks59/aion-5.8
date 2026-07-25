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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Holds the terrain of one world: an elevation and a surface code for every
 * cell of a square grid.
 * <p>
 * A cell covers {@link #CELL_SIZE} world units, so the grid spans
 * {@code (cols * CELL_SIZE)} by {@code (rows * CELL_SIZE)} units. Elevations
 * are quantised: the top fourteen bits of a cell hold the height in
 * thirty-seconds of a world unit, and the bottom two carry part of the surface
 * code that decides how the cell is walked.
 * <p>
 * The whole grid is held in memory. A world is a few megabytes and the ground
 * height is the query the server asks most often.
 *
 * @author Oraion
 */
public final class HeightMap {

	/** How many world units one cell spans, along each axis. */
	public static final int CELL_SIZE = 2;

	/** Turns a world coordinate into a cell coordinate, as the original shift does. */
	public static final int CELL_SHIFT = 1;

	/** Turns a stored elevation into world units. */
	public static final float HEIGHT_SCALE = 1.0f / 32.0f;

	/** Masks off the two surface-code bits, leaving the quantised elevation. */
	public static final int HEIGHT_MASK = 0xfffc;

	/**
	 * Names the file each tier is read from. The first that opens wins, so a
	 * world that ships several is read at the earliest one listed.
	 */
	public static final String[] TIER_FILES = { "HeightMap40.Dat", "HeightMap32.Dat", "HeightMap24.Dat" };

	/** The tier whose surface code is a byte of its own rather than packed across two grids. */
	public static final int TIER_40 = 0;

	/** The tier whose cells are stored coarser and unpacked as they are read. */
	public static final int TIER_24 = 2;

	private final int tier;
	private final GeoVersion version;
	private final int cols;
	private final int rows;
	private final int stride;
	private final short[] heights;
	private final byte[] materials;
	private final byte[] extra;

	private HeightMap(int tier, GeoVersion version, int cols, int rows, short[] heights, byte[] materials,
			byte[] extra) {
		this.tier = tier;
		this.version = version;
		this.cols = cols;
		this.rows = rows;
		this.stride = rows + 1;
		this.heights = heights;
		this.materials = materials;
		this.extra = extra;
	}

	/**
	 * Reads the terrain of one world.
	 *
	 * @param worldDirectory the world's geodata directory
	 * @return the terrain, or null if the world ships no tier at all
	 * @throws IOException if a tier is present but could not be read
	 */
	public static HeightMap load(Path worldDirectory) throws IOException {
		for (int tier = 0; tier < TIER_FILES.length; tier++) {
			Path file = worldDirectory.resolve(TIER_FILES[tier]);
			if (!Files.isRegularFile(file)) {
				continue;
			}
			return read(tier, file);
		}
		return null;
	}

	private static HeightMap read(int tier, Path file) throws IOException {
		ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
		GeoVersion version = GeoVersion.readHeader(buffer);

		if (buffer.remaining() < 8) {
			throw new IOException("Read no grid size from " + file + ".");
		}
		int cols = buffer.getInt();
		int rows = buffer.getInt();
		if (cols == 0 || rows == 0) {
			throw new IOException("Read an empty grid, " + cols + " by " + rows + ", from " + file + ".");
		}

		// The grid holds the corners of the cells, so it is one wider and one
		// taller than the cell count.
		int cells = (cols + 1) * (rows + 1);
		if (buffer.remaining() < cells * 3) {
			throw new IOException("Read a grid of " + cols + " by " + rows + " from " + file
					+ ", which the file is too short to hold.");
		}

		short[] heights = new short[cells];
		buffer.asShortBuffer().get(heights);
		buffer.position(buffer.position() + cells * 2);

		byte[] materials = new byte[cells];
		buffer.get(materials);

		byte[] extra = null;
		if (tier == TIER_40) {
			extra = new byte[cells];
			buffer.get(extra);
		}

		HeightMap map = new HeightMap(tier, version, cols, rows, heights, materials, extra);
		if (tier == TIER_24) {
			map.unpackCoarseTier();
		}
		return map;
	}

	/**
	 * Spreads a coarse cell into the layout the finer tiers already use.
	 * <p>
	 * The elevation moves up out of the way and the four bits of surface code
	 * split: the top two stay at the bottom of the elevation and the bottom two
	 * move to the top of the material byte. The query side puts them back
	 * together the same way round.
	 */
	private void unpackCoarseTier() {
		for (int i = 0; i < heights.length; i++) {
			int packed = heights[i] & 0xffff;
			heights[i] = (short) (((packed >> 3) & 0x1ffc) | ((packed & 0xf) >> 2));
			materials[i] |= (byte) (packed << 6);
		}
	}

	/** Answers which of the three tiers this world was read at. */
	public int getTier() {
		return tier;
	}

	public GeoVersion getVersion() {
		return version;
	}

	public int getCols() {
		return cols;
	}

	public int getRows() {
		return rows;
	}

	/** Answers how many cells one row of the grid holds. */
	public int getStride() {
		return stride;
	}

	/** Answers how many cells the grid holds in total. */
	public int getCellCount() {
		return heights.length;
	}

	/**
	 * Answers the index of one grid corner.
	 * <p>
	 * The grid runs down X in steps of a whole column, so a stride is the height
	 * of a column and not the width of a row. That is what makes the last corner
	 * land on the last cell: {@code stride * cols + rows} is exactly
	 * {@code (cols + 1) * (rows + 1) - 1}. Multiplying the other way round only
	 * agrees on a square world.
	 *
	 * @param cellX the corner's column
	 * @param cellY the corner's row
	 * @return the index into the grid
	 */
	public int cornerIndex(int cellX, int cellY) {
		return stride * cellX + cellY;
	}

	/**
	 * Answers the index of the cell a world position falls in.
	 *
	 * @param x world position along X
	 * @param y world position along Y
	 * @return the index into the grid
	 */
	public int cellIndex(int x, int y) {
		return cornerIndex(x >> CELL_SHIFT, y >> CELL_SHIFT);
	}

	/** Answers the raw cell, elevation and surface bits together. */
	public int rawCell(int index) {
		return heights[index] & 0xffff;
	}

	/** Answers the elevation of one grid corner, in world units. */
	public float heightAt(int index) {
		return (heights[index] & HEIGHT_MASK) * HEIGHT_SCALE;
	}

	public int materialAt(int index) {
		return materials[index] & 0xff;
	}

	/**
	 * Answers the third grid, which only the finest tier carries.
	 *
	 * @return one byte per cell, or null at the other two tiers
	 */
	public byte[] getExtra() {
		return extra;
	}
}
