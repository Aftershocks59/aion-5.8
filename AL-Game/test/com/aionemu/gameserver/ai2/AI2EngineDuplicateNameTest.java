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
package com.aionemu.gameserver.ai2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers what happens when two handlers claim the same AI name.
 * <p>
 * Fourteen names in data/scripts are declared by handlers sitting in different
 * packages, six of which do not agree on behaviour. The registry answered with a
 * plain map put, so the surviving handler depended on the order the filesystem
 * enumerated the scripts and nothing said a word about it. The overwrite still
 * stands, on purpose, but it now has to be reported.
 *
 * @author Oraion
 */
class AI2EngineDuplicateNameTest {

	private static final String SHARED_NAME = "test_shared_ai_name";

	@AIName(SHARED_NAME)
	private static final class FirstHandler extends NpcAI2 {
	}

	@AIName(SHARED_NAME)
	private static final class SecondHandler extends NpcAI2 {
	}

	@AIName("test_unique_ai_name")
	private static final class UniqueHandler extends NpcAI2 {
	}

	/** Carries no annotation, standing in for a helper the scan also walks over. */
	private static final class UnnamedHandler extends NpcAI2 {
	}

	@Test
	@DisplayName("Reports a name two different handlers claim")
	void reportsDuplicateName() {
		AI2Engine engine = new AI2Engine();

		engine.registerAI(FirstHandler.class);
		engine.registerAI(SecondHandler.class);

		assertEquals(1, engine.getDuplicateNames().size());
		assertTrue(engine.getDuplicateNames().contains(SHARED_NAME));
	}

	@Test
	@DisplayName("Keeps the last handler registered, as it always did")
	void keepsTheLastHandler() {
		AI2Engine engine = new AI2Engine();

		engine.registerAI(FirstHandler.class);
		engine.registerAI(SecondHandler.class);

		assertEquals(SecondHandler.class, engine.getRegisteredAI(SHARED_NAME));
	}

	@Test
	@DisplayName("Stays quiet when the same handler is registered twice")
	void ignoresRepeatOfTheSameHandler() {
		AI2Engine engine = new AI2Engine();

		engine.registerAI(FirstHandler.class);
		engine.registerAI(FirstHandler.class);

		assertTrue(engine.getDuplicateNames().isEmpty());
		assertEquals(FirstHandler.class, engine.getRegisteredAI(SHARED_NAME));
	}

	@Test
	@DisplayName("Stays quiet when every name is distinct")
	void ignoresDistinctNames() {
		AI2Engine engine = new AI2Engine();

		engine.registerAI(FirstHandler.class);
		engine.registerAI(UniqueHandler.class);

		assertTrue(engine.getDuplicateNames().isEmpty());
		assertEquals(FirstHandler.class, engine.getRegisteredAI(SHARED_NAME));
		assertEquals(UniqueHandler.class, engine.getRegisteredAI("test_unique_ai_name"));
	}

	@Test
	@DisplayName("Skips a handler that declares no name")
	void skipsHandlerWithoutAnnotation() {
		AI2Engine engine = new AI2Engine();

		engine.registerAI(UnnamedHandler.class);

		assertTrue(engine.getDuplicateNames().isEmpty());
		assertNull(engine.getRegisteredAI(SHARED_NAME));
	}

	@Test
	@DisplayName("Exposes the duplicate names as a read-only view")
	void exposesDuplicatesAsReadOnly() {
		AI2Engine engine = new AI2Engine();
		engine.registerAI(FirstHandler.class);
		engine.registerAI(SecondHandler.class);

		try {
			engine.getDuplicateNames().add("smuggled");
			throw new AssertionError("The duplicate set must not accept a write.");
		} catch (UnsupportedOperationException expected) {
			assertEquals(1, engine.getDuplicateNames().size());
		}
	}
}
