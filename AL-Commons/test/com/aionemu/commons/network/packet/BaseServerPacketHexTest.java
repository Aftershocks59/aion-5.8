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
package com.aionemu.commons.network.packet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the hexadecimal overload of writeB.
 * <p>
 * The overload was missing from the sources and only existed in a prebuilt jar,
 * so it had to be reimplemented. Seven server packets are built from captured
 * client dumps and depend on it byte for byte: an off-by-one here would corrupt
 * every one of them, and only a live client would notice.
 *
 * @author Oraion
 */
class BaseServerPacketHexTest {

	/** Exposes the protected writer through a minimal concrete packet. */
	private static final class TestPacket extends BaseServerPacket {

		TestPacket(int capacity) {
			setBuf(ByteBuffer.allocate(capacity));
		}

		void write(String hex) {
			writeB(hex);
		}

		byte[] written() {
			ByteBuffer buffer = buf;
			byte[] bytes = new byte[buffer.position()];
			buffer.flip();
			buffer.get(bytes);
			return bytes;
		}
	}

	@Test
	@DisplayName("Decodes a compact hexadecimal string")
	void decodesCompactHex() {
		TestPacket packet = new TestPacket(16);

		packet.write("0001FF");

		assertArrayEquals(new byte[] { 0x00, 0x01, (byte) 0xFF }, packet.written());
	}

	@Test
	@DisplayName("Ignores the whitespace of a spaced dump")
	void decodesSpacedHex() {
		TestPacket packet = new TestPacket(16);

		packet.write("00 01 FF");

		assertArrayEquals(new byte[] { 0x00, 0x01, (byte) 0xFF }, packet.written());
	}

	@Test
	@DisplayName("Ignores newlines and tabs inside a dump")
	void decodesHexSplitOverLines() {
		TestPacket packet = new TestPacket(16);

		packet.write("00 01\n\tFF\r\n42");

		assertArrayEquals(new byte[] { 0x00, 0x01, (byte) 0xFF, 0x42 }, packet.written());
	}

	@Test
	@DisplayName("Accepts lower case digits")
	void decodesLowerCaseHex() {
		TestPacket packet = new TestPacket(16);

		packet.write("deadbeef");

		assertArrayEquals(new byte[] { (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF }, packet.written());
	}

	@Test
	@DisplayName("Writes nothing for an empty string")
	void decodesEmptyHex() {
		TestPacket packet = new TestPacket(16);

		packet.write("");

		assertArrayEquals(new byte[0], packet.written());
	}

	@Test
	@DisplayName("Preserves the high bit of values above 0x7F")
	void preservesHighBitValues() {
		TestPacket packet = new TestPacket(16);

		packet.write("80 FF 7F");

		assertArrayEquals(new byte[] { (byte) 0x80, (byte) 0xFF, 0x7F }, packet.written());
	}

	@Test
	@DisplayName("Rejects a string that is not hexadecimal")
	void rejectsNonHexInput() {
		TestPacket packet = new TestPacket(16);

		assertThrows(NumberFormatException.class, () -> packet.write("ZZ"));
	}
}
