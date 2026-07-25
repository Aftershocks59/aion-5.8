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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.nio.ByteBuffer;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.commons.network.AConnection;

/**
 * Covers the bounds of byte array reads driven by client supplied lengths.
 * <p>
 * Several packets pass a length they just read from the wire straight into
 * readB. Unbounded, that let any logged-in player claim a two gigabyte chunk and
 * exhaust the heap, or claim a negative one and kill the packet thread with a
 * NegativeArraySizeException. A packet cannot legitimately hold more bytes than
 * it carries, so the remaining count is the bound.
 *
 * @author Oraion
 */
class BaseClientPacketReadTest {

	/** Exposes the protected reader through a minimal concrete packet. */
	private static final class TestPacket extends BaseClientPacket<AConnection> {

		TestPacket(byte[] content) {
			super(0);
			setBuffer(ByteBuffer.wrap(content));
		}

		@Override
		protected void readImpl() {
			// Driven by the tests instead.
		}

		@Override
		protected void runImpl() {
			// Nothing to run.
		}

		@Override
		public void run() {
			// Nothing to run.
		}

		byte[] read(int length) {
			return readB(length);
		}

		int remaining() {
			return getRemainingBytes();
		}
	}

	@Test
	@DisplayName("Reads exactly the requested bytes when the packet holds them")
	void readsRequestedBytes() {
		TestPacket packet = new TestPacket(new byte[] { 1, 2, 3, 4 });

		assertArrayEquals(new byte[] { 1, 2, 3 }, packet.read(3));
		assertEquals(1, packet.remaining());
	}

	@Test
	@DisplayName("Truncates a length larger than the packet instead of allocating it")
	void truncatesOversizedLength() {
		TestPacket packet = new TestPacket(new byte[] { 1, 2, 3 });

		byte[] read = packet.read(Integer.MAX_VALUE);

		assertArrayEquals(new byte[] { 1, 2, 3 }, read);
		assertEquals(0, packet.remaining());
	}

	@Test
	@DisplayName("Survives a two gigabyte claim without exhausting the heap")
	void rejectsHeapExhaustionAttempt() {
		TestPacket packet = new TestPacket(new byte[] { 42 });

		// Would allocate 2 GB before the bound existed. The timeout catches a
		// regression that starts allocating again rather than waiting for an
		// OutOfMemoryError to take the whole test JVM down.
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			byte[] read = packet.read(Integer.MAX_VALUE);
			assertEquals(1, read.length);
		});
	}

	@Test
	@DisplayName("Returns empty for a negative length rather than throwing")
	void rejectsNegativeLength() {
		TestPacket packet = new TestPacket(new byte[] { 1, 2, 3 });

		assertArrayEquals(new byte[0], packet.read(-1));
		assertArrayEquals(new byte[0], packet.read(Integer.MIN_VALUE));
		assertEquals(3, packet.remaining(), "A rejected read must not consume the buffer");
	}

	@Test
	@DisplayName("Returns empty for a zero length")
	void readsNothingForZeroLength() {
		TestPacket packet = new TestPacket(new byte[] { 1, 2, 3 });

		assertArrayEquals(new byte[0], packet.read(0));
		assertEquals(3, packet.remaining());
	}

	@Test
	@DisplayName("Returns empty once the packet is drained")
	void readsNothingWhenDrained() {
		TestPacket packet = new TestPacket(new byte[] { 1 });
		packet.read(1);

		assertArrayEquals(new byte[0], packet.read(10));
		assertEquals(0, packet.remaining());
	}
}
