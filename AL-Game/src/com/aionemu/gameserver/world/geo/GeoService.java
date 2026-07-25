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
import com.aionemu.gameserver.geodata.MaterialCollision;
import com.aionemu.gameserver.geodata.RayTriangle;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.MathUtil;

/**
 * Answers what the world's shape allows.
 * <p>
 * Geodata is switched on with {@code gameserver.geodata.enable}, and ships off.
 * Switched off no world is loaded, so every query here falls through to the
 * answer it would give on an empty world: the ground is wherever whoever asked
 * already stood, and nothing blocks sight or movement. That is what lets the
 * server run without the files, and it is the same path a world with no geodata
 * of its own takes.
 *
 * @author Oraion
 */
public class GeoService {

	private static final Logger log = LoggerFactory.getLogger(GeoService.class);

	/** Names the directory geodata is read from, one directory per world id. */
	private static final Path GEO_DIRECTORY = Paths.get("data", "geo");

	/**
	 * Names the two worlds sight is never blocked in.
	 * <p>
	 * They are read from geodata that does not describe what players can see
	 * through, and blocking on it hides them from each other.
	 */
	private static final int[] ALWAYS_VISIBLE = { 301110000, 301360000 };

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

		log.info("Geodata serves the ground, sight and movement of " + engine.getWorldCount() + " worlds.");
	}

	/** Answers the loaded geodata, for callers that read the world's shape directly. */
	public GeoEngine getEngine() {
		return engine;
	}

	public void setDoorState(int worldId, int instanceId, String name, boolean isOpened) {
		// Doors are objects whose collision moves. The files say which triangles
		// belong to one, but a door's state lives in the running server and the
		// mesh is not rebuilt for it yet.
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
		// With no height to measure nearness from there is no choosing between the
		// floors of a building, so this is the terrain and nothing above it.
		float newZ = engine.getTerrainZ(worldId, x, y, 0.0f);
		if (GeoDataConfig.GEO_ENABLE) {
			newZ += 0.001f;
		}
		return newZ;
	}

	public float getZW(int worldId, float x, float y) {
		return this.getZ(worldId, x, y);
	}

	/**
	 * Answers the surface under a position, keeping the position's own height
	 * where the surface is not below it.
	 * <p>
	 * Snapping only downwards is what the server has always done, and it is what
	 * stops a query from lifting whoever asked it up through a floor.
	 */
	private float groundUnder(int worldId, float x, float y, float z) {
		float ground = engine.getGroundZ(worldId, x, y, z, z);
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
		if (isAlwaysVisible(object.getWorldId())) {
			return true;
		}
		float reach = (float) (MathUtil.getDistance(object, target)
				- (double) target.getObjectTemplate().getBoundRadius().getCollision());
		if (reach <= 0.0f) {
			return true;
		}
		return this.canSee(object.getWorldId(), object.getX(), object.getY(), object.getZ() + eyeHeight(object, target),
				target.getX(), target.getY(), target.getZ() + eyeHeight(target, object), reach,
				object.getInstanceId());
	}

	public boolean canPass(VisibleObject object, VisibleObject target) {
		float reach = (float) (MathUtil.getDistance(object, target)
				- (double) target.getObjectTemplate().getBoundRadius().getCollision());
		if (reach <= 0.0f) {
			return true;
		}
		return this.canPass(object.getWorldId(), object.getX(), object.getY(), object.getZ() + eyeHeight(object, target),
				target.getX(), target.getY(), target.getZ() + eyeHeight(target, object), reach,
				object.getInstanceId());
	}

	/**
	 * Answers how far above its own position something is looked at, and looks
	 * from.
	 * <p>
	 * Half its height, capped: a tall creature seen from its middle rather than
	 * its crown is far likelier to be seen over the ground between. A player is
	 * a known height and is taken as one.
	 */
	private static float eyeHeight(VisibleObject object, VisibleObject other) {
		if (object instanceof Player) {
			return 1.5f;
		}
		float half = object.getObjectTemplate().getBoundRadius().getUpper() / 2.0f;
		return (double) half > 2.2 ? 2.2f : half;
	}

	public boolean canSee(int worldId, float x, float y, float z, float x1, float y1, float z1, float limit,
			int instanceId) {
		if (isAlwaysVisible(worldId)) {
			return true;
		}
		return engine.isClear(worldId, x, y, z, x1, y1, z1, MaterialCollision.COLUMN_MOVEMENT);
	}

	public boolean canPass(int worldId, float x, float y, float z, float x1, float y1, float z1, float limit,
			int instanceId) {
		return engine.isClear(worldId, x, y, z, x1, y1, z1, MaterialCollision.COLUMN_MOVEMENT);
	}

	public boolean canPassWalker(int worldId, float x, float y, float z, float x1, float y1, float z1, float limit,
			int instanceId) {
		return this.canPass(worldId, x, y, z, x1, y1, z1, limit, instanceId);
	}

	private static boolean isAlwaysVisible(int worldId) {
		for (int world : ALWAYS_VISIBLE) {
			if (world == worldId) {
				return true;
			}
		}
		return false;
	}

	public boolean isGeoOn() {
		return GeoDataConfig.GEO_ENABLE;
	}

	/**
	 * Answers how far towards a point something gets before the world stops it.
	 *
	 * @return where it ends up, which is the point itself where nothing is in the
	 *         way
	 */
	public Vector3f getClosestCollision(Creature object, float x, float y, float z, boolean changeDirection,
			byte intentions) {
		float fromZ = object.getZ() - 0.6f;
		float at = engine.firstHit(object.getWorldId(), object.getX(), object.getY(), fromZ, x, y, z,
				MaterialCollision.COLUMN_MOVEMENT);
		if (!RayTriangle.hit(at) || at < 0.0f || at > 1.0f) {
			return new Vector3f(x, y, z);
		}
		// Stop just short of what was hit rather than inside it.
		float stop = Math.max(0.0f, at - 0.01f);
		return new Vector3f(object.getX() + (x - object.getX()) * stop, object.getY() + (y - object.getY()) * stop,
				fromZ + (z - fromZ) * stop);
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
