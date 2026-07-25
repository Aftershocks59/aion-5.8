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
package com.aionemu.gameserver.dataholders.loadingutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.gameserver.world.zone.ZoneName;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * Covers restoring zone names from the binary snapshot.
 * <p>
 * ZoneName keeps one instance per name in a static registry, and callers compare
 * the instances with ==, for example PlayerController deciding whether a player
 * stands in a given zone. The registry is filled as a side effect of the JAXB
 * setter, so restoring the snapshot never filled it: ZoneName.get answered NONE
 * for all 33 names the server asks about by hand, every comparison went false,
 * and the behaviour attached to those zones quietly stopped applying. Booting
 * from XML reported no missing zone; booting from the snapshot reported 33.
 *
 * @author Oraion
 */
class ZoneNameSnapshotTest {

	/** Round trips one object through the snapshot's serialiser. */
	private static <T> T roundTrip(Kryo kryo, T value, Class<T> type) {
		Output output = new Output(4096, -1);
		kryo.writeObject(output, value);
		output.close();

		Input input = new Input(output.getBuffer(), 0, output.position());
		try {
			return kryo.readObject(input, type);
		} finally {
			input.close();
		}
	}

	@Test
	@DisplayName("Hands back the interned instance, not a copy")
	void restoresTheInternedInstance() {
		ZoneName original = ZoneName.createOrGet("TEST_SNAPSHOT_ZONE_A_110010000");

		ZoneName restored = roundTrip(BinaryStaticDataCache.newKryo(), original, ZoneName.class);

		// Copying the field would answer an equal but distinct object, which is what
		// broke every == comparison against the registry.
		assertSame(original, restored);
	}

	@Test
	@DisplayName("Leaves the name findable through the registry")
	void fillsTheRegistry() {
		ZoneName original = ZoneName.createOrGet("TEST_SNAPSHOT_ZONE_B_110010000");

		ZoneName restored = roundTrip(BinaryStaticDataCache.newKryo(), original, ZoneName.class);

		assertSame(restored, ZoneName.get("TEST_SNAPSHOT_ZONE_B_110010000"));
		assertNotSame(ZoneName.get(ZoneName.NONE), restored);
	}

	@Test
	@DisplayName("Keeps one instance for a name that appears several times")
	@SuppressWarnings("unchecked")
	void keepsOneInstancePerName() {
		ZoneName shared = ZoneName.createOrGet("TEST_SNAPSHOT_ZONE_C_110010000");
		List<ZoneName> names = new ArrayList<ZoneName>();
		names.add(shared);
		names.add(ZoneName.createOrGet("TEST_SNAPSHOT_ZONE_D_110010000"));
		names.add(shared);

		List<ZoneName> restored = roundTrip(BinaryStaticDataCache.newKryo(), names, (Class<List<ZoneName>>) (Class<?>) ArrayList.class);

		assertEquals(3, restored.size());
		assertSame(restored.get(0), restored.get(2));
		assertSame(shared, restored.get(0));
		assertNotSame(restored.get(0), restored.get(1));
	}

	@Test
	@DisplayName("Carries the name unchanged")
	void carriesTheName() {
		ZoneName original = ZoneName.createOrGet("TEST_SNAPSHOT_ZONE_E_110010000");

		ZoneName restored = roundTrip(BinaryStaticDataCache.newKryo(), original, ZoneName.class);

		assertEquals("TEST_SNAPSHOT_ZONE_E_110010000", restored.name());
	}
}
