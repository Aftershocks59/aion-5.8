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
package com.aionemu.gameserver.ai2.manager;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.gameserver.ai2.AI2;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;

/**
 * Covers deciding whether a target is close enough to hit.
 * <p>
 * The three checks were all present but ordered wrongly: canSee ran first and
 * dereferenced the target, so the null check behind it never got a say. A target
 * that died or left the world between the attack being scheduled and it firing
 * threw a NullPointerException out of the scheduler, which is what the live
 * server was logging on every fight.
 * <p>
 * Line of sight answers true without reading the target since the geodata engine
 * was replaced, so that one route into the fault is shut. The order is still
 * what keeps it shut once sight is served again.
 *
 * @author Oraion
 */
class SimpleAttackManagerTest {

	private static Npc npcWithTarget(VisibleObject target) {
		Npc npc = mock(Npc.class);
		AI2 ai = mock(AI2.class);
		when(ai.isLogging()).thenReturn(false);
		when(npc.getAi2()).thenReturn(ai);
		when(npc.getTarget()).thenReturn(target);
		return npc;
	}

	@Test
	@DisplayName("Calls off the attack when the target is gone")
	void refusesAMissingTarget() {
		Npc npc = npcWithTarget(null);

		// The geo service is never reached, so this needs no world.
		assertDoesNotThrow(() -> assertFalse(SimpleAttackManager.isTargetInAttackRange(npc)));
	}

	@Test
	@DisplayName("Calls off the attack when the target is not something that can be hit")
	void refusesANonCreatureTarget() {
		Npc npc = npcWithTarget(mock(VisibleObject.class));

		assertDoesNotThrow(() -> assertFalse(SimpleAttackManager.isTargetInAttackRange(npc)));
	}
}
