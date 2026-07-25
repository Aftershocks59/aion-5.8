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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the geodata of one world: its terrain, its materials and its collision.
 *
 * @author Oraion
 */
public final class WorldGeoData implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(WorldGeoData.class);

	private final int worldId;
	private final HeightMap terrain;
	private final MeshMaterialTable materials;
	private final CollisionGrid collision;

	private WorldGeoData(int worldId, HeightMap terrain, MeshMaterialTable materials, CollisionGrid collision) {
		this.worldId = worldId;
		this.terrain = terrain;
		this.materials = materials;
		this.collision = collision;
	}

	/**
	 * Reads the geodata of one world.
	 *
	 * @param worldId        the world
	 * @param worldDirectory the world's geodata directory
	 * @return the geodata, or null if the world ships none
	 * @throws IOException if the world ships geodata that could not be read
	 */
	public static WorldGeoData load(int worldId, Path worldDirectory) throws IOException {
		if (!Files.isDirectory(worldDirectory)) {
			return null;
		}

		HeightMap terrain = HeightMap.load(worldDirectory);
		if (terrain == null) {
			return null;
		}

		MeshMaterialTable materials = MeshMaterialTable.load(worldDirectory);
		// A world whose files were exported at different times does not describe
		// one consistent world. The original only says so and carries on.
		if (!materials.getVersion().equals(terrain.getVersion())) {
			log.warn("World " + worldId + ": " + MeshMaterialTable.FILE_NAME + " was exported at a different version"
					+ " from " + HeightMap.TIER_FILES[terrain.getTier()] + ".");
		}

		CollisionGrid collision = CollisionGrid.load(worldDirectory, terrain);
		if (!collision.getVersion().equals(materials.getVersion())) {
			log.warn("World " + worldId + ": " + CollisionGrid.FILE_NAME + " was exported at a different version"
					+ " from " + MeshMaterialTable.FILE_NAME + ".");
		}

		return new WorldGeoData(worldId, terrain, materials, collision);
	}

	public int getWorldId() {
		return worldId;
	}

	public HeightMap getTerrain() {
		return terrain;
	}

	public MeshMaterialTable getMaterials() {
		return materials;
	}

	public CollisionGrid getCollision() {
		return collision;
	}

	@Override
	public void close() throws IOException {
		collision.close();
	}
}
