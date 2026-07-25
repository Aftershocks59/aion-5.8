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
package com.aionemu.gameserver.world.geo;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.GeoDataConfig;
import com.aionemu.gameserver.geoEngine.collision.CollisionResults;
import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.geodata.GeoEngine;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;

/**
 * Answers what the world's shape allows.
 * <p>
 * Geodata is switched on with {@code gameserver.geodata.enable}, and ships off.
 * Switched off, every query here answers the way it would on an empty world:
 * the ground is wherever the caller already stood, and nothing blocks sight or
 * movement. That is what lets the server run without the files.
 * <p>
 * Switched on, the ground comes from the height grid the client ships. Sight
 * and movement do not yet: the traversal the original walks its collision mesh
 * with is not reproduced, so those still answer as they do switched off rather
 * than answer wrongly.
 *
 * @author Oraion
 */
public class GeoService {

	private static final Logger log = LoggerFactory.getLogger(GeoService.class);

	/** Names the directory geodata is read from, one directory per world id. */
	private static final Path GEO_DIRECTORY = Paths.get("data", "geo");

	private static final List<Integer> npcsExclude = new ArrayList<Integer>();

	private GeoEngine engine = GeoEngine.empty();

	public static List<Integer> getNpcsExclude() {
		return npcsExclude;
	}

	public static final GeoService getInstance() {
		return SingletonHolder.instance;
	}

	public void initializeGeo() {
		if (!GeoDataConfig.GEO_ENABLE) {
			log.info("Geodata is switched off. The world has no collision and no line of sight.");
			return;
		}

		try {
			engine = GeoEngine.load(GEO_DIRECTORY);
		} catch (IOException e) {
			// Losing geodata is not worth losing the server for: it degrades to
			// exactly what running with it switched off does.
			log.error("Failed to read the geodata under " + GEO_DIRECTORY + ". Running without it.", e);
			engine = GeoEngine.empty();
			return;
		}

		log.info("Geodata answers the ground height of " + engine.getWorldCount()
				+ " worlds. Line of sight and collision are not served yet.");
	}

	/** Answers the loaded geodata, for callers that read the world's shape directly. */
	public GeoEngine getEngine() {
		return engine;
	}

	public void setDoorState(int worldId, int instanceId, String name, boolean isOpened) {
		// Doors live in the collision mesh, which is not queried yet.
	}

	public float getZAfterMoveBehind(int worldId, float x, float y, float z, int instanceId) {
		if (GeoDataConfig.GEO_ENABLE) {
			return this.getZ(worldId, x, y, z, 0.0f, instanceId);
		}
		return this.getZ(worldId, x, y, z, 0.5f, instanceId);
	}

	public float getZ(VisibleObject object) {
		float newZ = this.groundUnder(object.getWorldId(), object.getX(), object.getY(), object.getZ());
		if (GeoDataConfig.GEO_ENABLE) {
			newZ += 0.001f;
		}
		return newZ;
	}

	public float getZ(int worldId, float x, float y, float z, float defaultUp, int instanceId) {
		float newZ = this.groundUnder(worldId, x, y, z);
		if (GeoDataConfig.GEO_ENABLE && defaultUp != 100.0f) {
			newZ += 0.001f;
		}
		return newZ;
	}

	public float getZW(int worldId, float x, float y, float z, float defaultUp, int instanceId) {
		return this.getZ(worldId, x, y, z, defaultUp, instanceId);
	}

	public float getZ(int worldId, float x, float y) {
		float newZ = engine.getGroundZ(worldId, x, y, 0.0f);
		if (GeoDataConfig.GEO_ENABLE) {
			newZ += 0.001f;
		}
		return newZ;
	}

	public float getZW(int worldId, float x, float y) {
		return this.getZ(worldId, x, y);
	}

	/**
	 * Answers the ground under a position, keeping the position's own height
	 * where the ground is not below it.
	 * <p>
	 * Snapping only downwards is what the server has always done, and it is what
	 * stops a query from lifting whoever asked it up through a floor.
	 */
	private float groundUnder(int worldId, float x, float y, float z) {
		float ground = engine.getGroundZ(worldId, x, y, z);
		if (ground > 0.0f && ground < z + 2.0f) {
			return ground;
		}
		return z;
	}

	public String getDoorName(int worldId, String meshFile, float x, float y, float z) {
		return null;
	}

	public CollisionResults getCollisions(VisibleObject object, float x, float y, float z, boolean changeDirection,
			byte intentions) {
		return new CollisionResults(intentions, false, object.getInstanceId());
	}

	public boolean canSee(VisibleObject object, VisibleObject target) {
		return true;
	}

	public boolean canPass(VisibleObject object, VisibleObject target) {
		return true;
	}

	public boolean canSee(int worldId, float x, float y, float z, float x1, float y1, float z1, float limit,
			int instanceId) {
		return true;
	}

	public boolean canPass(int worldId, float x, float y, float z, float x1, float y1, float z1, float limit,
			int instanceId) {
		return true;
	}

	public boolean canPassWalker(int worldId, float x, float y, float z, float x1, float y1, float z1, float limit,
			int instanceId) {
		return true;
	}

	public boolean isGeoOn() {
		return GeoDataConfig.GEO_ENABLE;
	}

	public Vector3f getClosestCollision(Creature object, float x, float y, float z, boolean changeDirection,
			byte intentions) {
		return new Vector3f(x, y, z);
	}

	/** Releases the files the geodata holds open. */
	public void shutdown() {
		engine.close();
		engine = GeoEngine.empty();
	}

	private static final class SingletonHolder {

		protected static final GeoService instance = new GeoService();

		private SingletonHolder() {
		}
	}
}
