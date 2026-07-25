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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Tiles one world into sectors, each holding the collision faces of a square
 * block of terrain.
 * <p>
 * The file stays mapped rather than read: the worlds together run to gigabytes
 * of collision, of which a running server touches only what its players stand
 * near. Mapping lets the operating system keep that much and no more.
 * <p>
 * A sector covers {@link #SECTOR_CELLS} terrain cells along each axis, so
 * {@link #SECTOR_SIZE} world units, and is divided into that many sub-cells of
 * one terrain cell each. Every sub-cell names how many faces it owns; the faces
 * themselves run one after another through the sector's block.
 * <p>
 * Closing releases the channel but not the mapping, which lives until the
 * garbage collector takes it. On Windows that keeps the file locked, so
 * geodata cannot be replaced under a running server. Restart it instead.
 *
 * @author Oraion
 */
public final class CollisionGrid implements AutoCloseable {

	/** The file a world's collision is read from. */
	public static final String FILE_NAME = "Collision.Dat";

	/** How many terrain cells a sector spans, along each axis. */
	public static final int SECTOR_CELLS = 32;

	/** How many world units a sector spans, along each axis. */
	public static final int SECTOR_SIZE = SECTOR_CELLS * HeightMap.CELL_SIZE;

	/** Turns a terrain cell coordinate into a sector coordinate. */
	public static final int SECTOR_SHIFT = 5;

	/** How many sub-cells a sector holds. */
	public static final int SUB_CELLS = SECTOR_CELLS * SECTOR_CELLS;

	/** How long a sector's face-count index runs, one short per sub-cell. */
	public static final int INDEX_SIZE = SUB_CELLS * 2;

	private final FileChannel channel;
	private final MappedByteBuffer file;
	private final GeoVersion version;
	private final int cols;
	private final int rows;

	/** Where each sector's face-count index starts, or -1 for a sector with no faces. */
	private final int[] indexOffsets;

	/** Where each sector's faces start. */
	private final int[] meshOffsets;

	/** How many bytes of faces each sector owns. */
	private final int[] meshSizes;

	private CollisionGrid(FileChannel channel, MappedByteBuffer file, GeoVersion version, int cols, int rows,
			int[] indexOffsets, int[] meshOffsets, int[] meshSizes) {
		this.channel = channel;
		this.file = file;
		this.version = version;
		this.cols = cols;
		this.rows = rows;
		this.indexOffsets = indexOffsets;
		this.meshOffsets = meshOffsets;
		this.meshSizes = meshSizes;
	}

	/**
	 * Maps the collision of one world and notes where each sector sits.
	 * <p>
	 * Only the sector sizes are read here, four bytes apiece; the faces stay on
	 * disk until something asks for them.
	 *
	 * @param worldDirectory the world's geodata directory
	 * @param terrain        the world's terrain, which settles the sector count
	 * @return the grid, never null
	 * @throws IOException if the file is missing or does not read as expected
	 */
	public static CollisionGrid load(Path worldDirectory, HeightMap terrain) throws IOException {
		Path path = worldDirectory.resolve(FILE_NAME);
		FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
		try {
			MappedByteBuffer file = channel.map(FileChannel.MapMode.READ_ONLY, 0, Files.size(path));
			file.order(ByteOrder.LITTLE_ENDIAN);

			GeoVersion version = GeoVersion.readHeader(file);

			int cols = sectorCount(terrain.getCols());
			int rows = sectorCount(terrain.getRows());
			int sectors = cols * rows;

			int[] indexOffsets = new int[sectors];
			int[] meshOffsets = new int[sectors];
			int[] meshSizes = new int[sectors];

			for (int sector = 0; sector < sectors; sector++) {
				int size = file.getInt();
				meshSizes[sector] = size;
				// A sector with nothing in it writes its size and stops: no index
				// and no faces follow.
				if (size < 1) {
					indexOffsets[sector] = -1;
					meshOffsets[sector] = -1;
					continue;
				}
				indexOffsets[sector] = file.position();
				meshOffsets[sector] = file.position() + INDEX_SIZE;
				int next = meshOffsets[sector] + size;
				if (next > file.limit()) {
					throw new IOException("Sector " + sector + " of " + path + " claims " + size
							+ " bytes of faces, which the file is too short to hold.");
				}
				file.position(next);
			}

			if (file.hasRemaining()) {
				throw new IOException("Left " + file.remaining() + " bytes unread at the end of " + path + ".");
			}

			return new CollisionGrid(channel, file, version, cols, rows, indexOffsets, meshOffsets, meshSizes);
		} catch (IOException | RuntimeException e) {
			channel.close();
			throw e;
		}
	}

	/**
	 * Answers how many sectors span a given number of terrain cells.
	 * <p>
	 * The division rounds towards zero, as the original does by nudging a
	 * negative count before shifting it.
	 */
	static int sectorCount(int cells) {
		return ((cells >> 31 & (SECTOR_CELLS - 1)) + cells) >> SECTOR_SHIFT;
	}

	public GeoVersion getVersion() {
		return version;
	}

	/** Answers how many sectors span the world along X. */
	public int getCols() {
		return cols;
	}

	/** Answers how many sectors span the world along Y. */
	public int getRows() {
		return rows;
	}

	public int getSectorCount() {
		return indexOffsets.length;
	}

	/** Answers whether a sector holds any collision at all. */
	public boolean isEmpty(int sector) {
		return meshSizes[sector] < 1;
	}

	/** Answers how many bytes of faces a sector owns. */
	public int getMeshSize(int sector) {
		return meshSizes[sector];
	}

	/** Answers the index of the sector holding a terrain cell. */
	public int sectorIndex(int cellX, int cellY) {
		return (cellY >> SECTOR_SHIFT) * cols + (cellX >> SECTOR_SHIFT);
	}

	/**
	 * Answers how many faces one sub-cell of a sector owns.
	 *
	 * @param sector   the sector
	 * @param subCell  the sub-cell, row-major within the sector
	 * @return the face count, zero for a sub-cell with none
	 */
	public int faceCount(int sector, int subCell) {
		if (isEmpty(sector)) {
			return 0;
		}
		return file.getShort(indexOffsets[sector] + subCell * 2) & 0xffff;
	}

	/**
	 * Answers a sector's faces, positioned at the first one.
	 * <p>
	 * The faces of the sub-cells run in order, so walking to a given sub-cell
	 * means stepping over the faces of every sub-cell before it.
	 *
	 * @param sector the sector
	 * @return a buffer over the sector's faces, or null for a sector with none
	 */
	public ByteBuffer faces(int sector) {
		if (isEmpty(sector)) {
			return null;
		}
		ByteBuffer faces = file.duplicate().order(ByteOrder.LITTLE_ENDIAN);
		faces.position(meshOffsets[sector]);
		faces.limit(meshOffsets[sector] + meshSizes[sector]);
		return faces.slice().order(ByteOrder.LITTLE_ENDIAN);
	}

	@Override
	public void close() throws IOException {
		channel.close();
	}
}
