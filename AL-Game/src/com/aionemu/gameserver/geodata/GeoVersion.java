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

import java.nio.ByteBuffer;

/**
 * Stamps a geodata file with the export it came from.
 * <p>
 * The three files of a world carry the same sixteen bytes, and the loader
 * compares them: a world whose height map, materials and collision were
 * exported at different times does not describe one consistent world.
 *
 * @author Oraion
 */
public final class GeoVersion {

	/** Opens every geodata file. Stored little-endian, so the bytes read 55 AA 55 AA. */
	public static final int MAGIC = 0xAA55AA55;

	/** Precedes the payload of every geodata file, magic and version included. */
	public static final int HEADER_SIZE = 0x80;

	/** Answers for a file whose header carried no magic, and so no version. */
	public static final GeoVersion NONE = new GeoVersion(0L, 0L);

	private final long low;
	private final long high;

	private GeoVersion(long low, long high) {
		this.low = low;
		this.high = high;
	}

	/**
	 * Reads the header a geodata file opens with.
	 * <p>
	 * A header that does not carry the magic is not a header: the buffer is put
	 * back where it started and the payload is read from byte zero, as the
	 * original loader does with its rewind.
	 *
	 * @param buffer positioned at the start of the file
	 * @return the version, or {@link #NONE} if the file carried no magic
	 */
	public static GeoVersion readHeader(ByteBuffer buffer) {
		int start = buffer.position();
		if (buffer.remaining() < HEADER_SIZE) {
			return NONE;
		}

		int magic = buffer.getInt(start);
		if (magic != MAGIC) {
			buffer.position(start);
			return NONE;
		}

		GeoVersion version = new GeoVersion(buffer.getLong(start + 4), buffer.getLong(start + 12));
		buffer.position(start + HEADER_SIZE);
		return version;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof GeoVersion)) {
			return false;
		}
		GeoVersion that = (GeoVersion) other;
		return low == that.low && high == that.high;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(low) * 31 + Long.hashCode(high);
	}

	@Override
	public String toString() {
		return String.format("%016x%016x", Long.reverseBytes(low), Long.reverseBytes(high));
	}
}
