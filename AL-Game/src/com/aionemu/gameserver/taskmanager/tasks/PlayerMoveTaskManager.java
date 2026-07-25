/*

 *
 *  Encom is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Encom is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser Public License
 *  along with Encom.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.gameserver.taskmanager.tasks;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.taskmanager.AbstractPeriodicTaskManager;


/**
 * @author ATracer
 */
public class PlayerMoveTaskManager extends AbstractPeriodicTaskManager {

	private final Map<Integer, Creature> movingPlayers = new ConcurrentHashMap<Integer, Creature>();

	private PlayerMoveTaskManager() {
		super(200);
	}

	public void addPlayer(Creature player) {
		movingPlayers.put(player.getObjectId(), player);
	}

	public void removePlayer(Creature player) {
		movingPlayers.remove(player.getObjectId());
	}

	@Override
	public void run() {
		for (Map.Entry<Integer, Creature> e : movingPlayers.entrySet()) {
			Creature player = e.getValue();
			player.getMoveController().moveToDestination();
		}
	}

	public static final PlayerMoveTaskManager getInstance() {
		return SingletonHolder.INSTANCE;
	}

	private static final class SingletonHolder {

		private static final PlayerMoveTaskManager INSTANCE = new PlayerMoveTaskManager();
	}
}