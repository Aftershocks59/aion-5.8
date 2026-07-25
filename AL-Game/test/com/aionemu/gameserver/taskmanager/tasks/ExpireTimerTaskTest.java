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
package com.aionemu.gameserver.taskmanager.tasks;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.aionemu.gameserver.model.IExpirable;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * Covers removing a player from the expiring effect table.
 * <p>
 * The removal walked the map with a for-each and called remove on it, which a
 * java.util map answers with ConcurrentModificationException where the
 * collection it replaced tolerated it. The throw landed on the leave-world path,
 * so a disconnect left the player half removed and unable to log back in. It
 * needed at least two entries to show, one being removed on the last iteration.
 *
 * @author Oraion
 */
class ExpireTimerTaskTest {

	/** Stands in for an expiring effect, with none of the game behaviour. */
	private static final class StubExpirable implements IExpirable {

		@Override
		public int getExpireTime() {
			return Integer.MAX_VALUE;
		}

		@Override
		public void expireEnd(Player player) {
			// Nothing to end.
		}

		@Override
		public boolean canExpireNow() {
			return false;
		}

		@Override
		public void expireMessage(Player player, int time) {
			// Nothing to announce.
		}
	}

	@Test
	@DisplayName("Removes every entry of one player without throwing")
	void removesAllEntriesOfOnePlayer() {
		ExpireTimerTask task = new ExpireTimerTask();
		Player player = mock(Player.class);

		task.addTask(new StubExpirable(), player);
		task.addTask(new StubExpirable(), player);
		task.addTask(new StubExpirable(), player);

		assertDoesNotThrow(() -> task.removePlayer(player));
	}

	@Test
	@DisplayName("Leaves other players untouched while removing one")
	void removesOnlyTheGivenPlayer() {
		ExpireTimerTask task = new ExpireTimerTask();
		Player leaving = mock(Player.class);
		Player staying = mock(Player.class);

		task.addTask(new StubExpirable(), leaving);
		task.addTask(new StubExpirable(), staying);
		task.addTask(new StubExpirable(), leaving);

		assertDoesNotThrow(() -> task.removePlayer(leaving));
		// The player who stayed must still be removable, which only holds if the
		// first pass left the map consistent.
		assertDoesNotThrow(() -> task.removePlayer(staying));
	}

	@Test
	@DisplayName("Accepts a player that has no entry")
	void removesUnknownPlayer() {
		ExpireTimerTask task = new ExpireTimerTask();

		assertDoesNotThrow(() -> task.removePlayer(mock(Player.class)));
	}

	@Test
	@DisplayName("Accepts a player whose only entry was already removed")
	void removesTwice() {
		ExpireTimerTask task = new ExpireTimerTask();
		Player player = mock(Player.class);
		task.addTask(new StubExpirable(), player);

		task.removePlayer(player);

		assertDoesNotThrow(() -> task.removePlayer(player));
	}
}
