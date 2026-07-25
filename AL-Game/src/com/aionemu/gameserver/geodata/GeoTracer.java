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

import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Runs a ray through one world and answers what it meets.
 * <p>
 * A cell is made of up to three things, which the client picks between by the
 * cell's four-bit surface code:
 * <ul>
 * <li>ground of its own, either a square drawn through the four corner heights
 * or a level square at one height, or none at all;</li>
 * <li>collision triangles laid over it, held in the cell's runs;</li>
 * <li>objects whose collision moves, which live in the running server rather
 * than in the files and are not answered here.</li>
 * </ul>
 * The ground is never stored: it is built from the height grid as it is needed.
 * The triangles are, and are named by index into the world's collision mesh.
 * <p>
 * One tracer serves one query at a time. It keeps a corner buffer to save
 * allocating one per triangle, so do not share it across threads.
 *
 * @author Oraion
 */
public final class GeoTracer {

	/** How far along its major axis one step of the walk moves. */
	public static final float STEP = HeightMap.CELL_SIZE;

	/** How short a ray counts as no ray at all. */
	public static final float MINIMUM_LENGTH = 0.0316f;

	/** How high a ground query looks from. */
	public static final float SWEEP_TOP = 4000.0f;

	/** How low a ground query looks to. */
	public static final float SWEEP_BOTTOM = -1000.0f;

	private final WorldGeoData world;
	private final Set<Integer> openDoors;
	private final float[] corners = new float[9];

	/** Answers a tracer for a world whose doors all stand shut. */
	public GeoTracer(WorldGeoData world) {
		this(world, null);
	}

	/**
	 * Answers a tracer for one instance of a world.
	 *
	 * @param world     the world's geodata
	 * @param openDoors the editor ids of the doors standing open in this
	 *                  instance, or null where none are
	 */
	public GeoTracer(WorldGeoData world, Set<Integer> openDoors) {
		this.world = world;
		this.openDoors = openDoors;
	}

	/**
	 * Answers how far along a ray it first meets the world.
	 *
	 * @param x      where the ray starts, along X
	 * @param y      where the ray starts, along Y
	 * @param z      where the ray starts, along Z
	 * @param toX    where the ray ends, along X
	 * @param toY    where the ray ends, along Y
	 * @param toZ    where the ray ends, along Z
	 * @param column which column of the material table decides what blocks
	 * @return the fraction of the way at which it meets, or
	 *         {@link RayTriangle#MISS}
	 */
	public float firstHit(float x, float y, float z, float toX, float toY, float toZ, int column) {
		float wayX = toX - x;
		float wayY = toY - y;
		float wayZ = toZ - z;

		// A ray this short is not a ray. The original rejects it before walking.
		if (wayX * wayX + wayY * wayY + wayZ * wayZ < MINIMUM_LENGTH * MINIMUM_LENGTH) {
			return RayTriangle.MISS;
		}

		boolean alongY = Math.abs(wayX) <= Math.abs(wayY);
		float majorFrom = alongY ? y : x;
		float majorTo = alongY ? toY : toX;
		float minorFrom = alongY ? x : y;
		float minorTo = alongY ? toX : toY;

		int firstCell = cellOf(majorFrom);
		int lastCell = cellOf(majorTo);
		int steps = Math.abs(lastCell - firstCell) + 1;
		float forward = majorTo >= majorFrom ? STEP : -STEP;
		float lean = majorTo == majorFrom ? 0.0f : (minorTo - minorFrom) / (majorTo - majorFrom) * forward;

		float lowZ = Math.min(z, toZ);
		float highZ = Math.max(z, toZ);

		float best = RayTriangle.MISS;
		int wasX = 0;
		int wasY = 0;
		for (int step = 0; step < steps; step++) {
			float major = majorFrom + forward * step;
			float minor = minorFrom + lean * step;
			int cellX = cellOf(alongY ? minor : major);
			int cellY = cellOf(alongY ? major : minor);

			best = nearer(best, hitInCell(cellX, cellY, x, y, z, wayX, wayY, wayZ, column, lowZ, highZ));

			// Stepping diagonally skips the two cells the line clips on its way
			// across the corner. Test them too, or a wall along the diagonal has a
			// gap in it.
			if (step > 0 && cellX != wasX && cellY != wasY) {
				best = nearer(best, hitInCell(wasX, cellY, x, y, z, wayX, wayY, wayZ, column, lowZ, highZ));
				best = nearer(best, hitInCell(cellX, wasY, x, y, z, wayX, wayY, wayZ, column, lowZ, highZ));
			}
			wasX = cellX;
			wasY = cellY;
		}

		return nearer(best, hitDoors(x, y, z, wayX, wayY, wayZ, column));
	}

	/**
	 * Answers what a ray meets among the world's doors.
	 * <p>
	 * A door's triangles never leave the world: opening one only stops it being
	 * counted. So a door is asked whether it is standing, then whether the ray
	 * passes through the heights it occupies, and only then for its triangles.
	 */
	private float hitDoors(float x, float y, float z, float wayX, float wayY, float wayZ, int column) {
		float best = RayTriangle.MISS;
		float endZ = z + wayZ;
		for (FieldObject object : world.getMesh().getFieldObjects()) {
			if (!object.blocksWhenShut() || isOpen(object)) {
				continue;
			}
			if (!(object.getLowZ() <= z || object.getLowZ() <= endZ)) {
				continue;
			}
			if (!(z <= object.getHighZ() || endZ <= object.getHighZ())) {
				continue;
			}
			best = nearer(best, hitInRange(object.getFirstTriangle(), object.getLastTriangle(), x, y, z, wayX, wayY,
					wayZ, column));
		}
		return best;
	}

	private boolean isOpen(FieldObject object) {
		return openDoors != null && openDoors.contains(Integer.valueOf(object.getEditorId()));
	}

	/** Answers what a ray meets among a run of the world's triangles. */
	private float hitInRange(int first, int last, float x, float y, float z, float wayX, float wayY, float wayZ,
			int column) {
		CollisionMesh mesh = world.getMesh();
		float best = RayTriangle.MISS;
		for (int triangle = first; triangle < last; triangle++) {
			if (!MaterialCollision.blocks(mesh.materialOf(triangle), column)) {
				continue;
			}
			best = nearer(best, RayTriangle.intersect(mesh.getVertices(), mesh.firstVertexOf(triangle),
					mesh.secondVertexOf(triangle), mesh.thirdVertexOf(triangle), x, y, z, wayX, wayY, wayZ));
		}
		return best;
	}

	/**
	 * Answers whether a ray runs from one point to the other without meeting
	 * anything.
	 *
	 * @param column which column of the material table decides what blocks
	 * @return true if nothing is in the way
	 */
	public boolean isClear(float x, float y, float z, float toX, float toY, float toZ, int column) {
		float hit = firstHit(x, y, z, toX, toY, toZ, column);
		return !RayTriangle.hit(hit) || hit < 0.0f || hit > 1.0f;
	}

	/**
	 * Answers the height of the surface nearest a position, looking straight down
	 * the world and back up it.
	 *
	 * @param x world position along X
	 * @param y world position along Y
	 * @param z the height to measure nearness from
	 * @return the surface height, or {@link RayTriangle#MISS} where the cell
	 *         carries no surface at all
	 */
	public float groundZ(float x, float y, float z) {
		int cellX = cellOf(x);
		int cellY = cellOf(y);
		if (!holds(cellX, cellY)) {
			return RayTriangle.MISS;
		}

		float way = SWEEP_BOTTOM - SWEEP_TOP;
		float best = RayTriangle.MISS;
		float bestGap = Float.MAX_VALUE;

		// A door that is shut is something to stand on, as much as a floor is.
		float onDoor = hitDoors(x, y, SWEEP_TOP, 0.0f, 0.0f, way, MaterialCollision.COLUMN_MOVEMENT);
		if (RayTriangle.hit(onDoor)) {
			best = SWEEP_TOP + way * onDoor;
			bestGap = Math.abs(z - best);
		}

		HeightMap terrain = world.getTerrain();
		int code = terrain.surfaceCode(terrain.cornerIndex(cellX, cellY));

		if (HeightMap.hasQuad(code) || HeightMap.isFlat(code)) {
			for (int half = 0; half < 2; half++) {
				groundCorners(terrain, cellX, cellY, HeightMap.isFlat(code), half);
				float at = RayTriangle.intersect(corners, x, y, SWEEP_TOP, 0.0f, 0.0f, way);
				if (RayTriangle.hit(at)) {
					float height = SWEEP_TOP + way * at;
					float gap = Math.abs(z - height);
					if (gap < bestGap) {
						bestGap = gap;
						best = height;
					}
				}
			}
		}

		if (HeightMap.hasMesh(code)) {
			ByteBuffer runs = runsOf(cellX, cellY);
			if (runs != null) {
				int count = world.getCollision().faceCount(sectorOf(cellX, cellY), subCellOf(cellX, cellY));
				CollisionMesh mesh = world.getMesh();
				for (int run = 0; run < count; run++) {
					int zKey = runs.getShort();
					int entries = runs.getShort() & 0xffff;
					if (quantise(SWEEP_TOP) < zKey) {
						break;
					}
					for (int entry = 0; entry < entries; entry++) {
						int packed = runs.getInt();
						if ((packed & CollisionGrid.ENTRY_IGNORED) != 0) {
							continue;
						}
						int triangle = packed & CollisionGrid.ENTRY_TRIANGLE_MASK;
						if (triangle >= mesh.getTriangleCount()) {
							continue;
						}
						float at = RayTriangle.intersect(mesh.getVertices(), mesh.firstVertexOf(triangle),
								mesh.secondVertexOf(triangle), mesh.thirdVertexOf(triangle), x, y, SWEEP_TOP, 0.0f,
								0.0f, way);
						if (RayTriangle.hit(at)) {
							float height = SWEEP_TOP + way * at;
							float gap = Math.abs(z - height);
							if (gap < bestGap) {
								bestGap = gap;
								best = height;
							}
						}
					}
				}
			}
		}

		return best;
	}

	/** Answers what a ray meets inside one cell, as a fraction of its way. */
	private float hitInCell(int cellX, int cellY, float x, float y, float z, float wayX, float wayY, float wayZ,
			int column, float lowZ, float highZ) {
		if (!holds(cellX, cellY)) {
			return RayTriangle.MISS;
		}

		HeightMap terrain = world.getTerrain();
		int code = terrain.surfaceCode(terrain.cornerIndex(cellX, cellY));
		float best = RayTriangle.MISS;

		// The ground a cell carries of its own is never in the material table: it
		// is terrain, and terrain always blocks.
		if (HeightMap.hasQuad(code) || HeightMap.isFlat(code)) {
			for (int half = 0; half < 2; half++) {
				groundCorners(terrain, cellX, cellY, HeightMap.isFlat(code), half);
				best = nearer(best, RayTriangle.intersect(corners, x, y, z, wayX, wayY, wayZ));
			}
		}

		if (!HeightMap.hasMesh(code)) {
			return best;
		}

		ByteBuffer runs = runsOf(cellX, cellY);
		if (runs == null) {
			return best;
		}

		int count = world.getCollision().faceCount(sectorOf(cellX, cellY), subCellOf(cellX, cellY));
		CollisionMesh mesh = world.getMesh();
		int ceiling = quantise(highZ);
		for (int run = 0; run < count; run++) {
			int zKey = runs.getShort();
			int entries = runs.getShort() & 0xffff;
			// Runs climb, so once one starts above the ray none of the rest can
			// reach it either.
			if (ceiling < zKey) {
				break;
			}
			for (int entry = 0; entry < entries; entry++) {
				int packed = runs.getInt();
				// The client marks the entries it does not collide with as it
				// loads: materials that are not solid, and triangles with no
				// usable normal.
				if ((packed & CollisionGrid.ENTRY_IGNORED) != 0) {
					continue;
				}
				int triangle = packed & CollisionGrid.ENTRY_TRIANGLE_MASK;
				if (triangle >= mesh.getTriangleCount()) {
					continue;
				}
				if (!MaterialCollision.blocks(mesh.materialOf(triangle), column)) {
					continue;
				}
				best = nearer(best, RayTriangle.intersect(mesh.getVertices(), mesh.firstVertexOf(triangle),
						mesh.secondVertexOf(triangle), mesh.thirdVertexOf(triangle), x, y, z, wayX, wayY, wayZ));
			}
		}
		return best;
	}

	/**
	 * Fills the corner buffer with half of a cell's ground.
	 *
	 * @param flat true where the cell is level, so all four corners share the
	 *             height of the first
	 * @param half which of the two triangles the square is drawn as
	 */
	private void groundCorners(HeightMap terrain, int cellX, int cellY, boolean flat, int half) {
		float lowX = cellX * HeightMap.CELL_SIZE;
		float lowY = cellY * HeightMap.CELL_SIZE;
		float highX = lowX + HeightMap.CELL_SIZE;
		float highY = lowY + HeightMap.CELL_SIZE;

		float atLow = terrain.heightAt(terrain.cornerIndex(cellX, cellY));
		float atHighY = flat ? atLow : terrain.heightAt(terrain.cornerIndex(cellX, cellY + 1));
		float atHighX = flat ? atLow : terrain.heightAt(terrain.cornerIndex(cellX + 1, cellY));
		float atBoth = flat ? atLow : terrain.heightAt(terrain.cornerIndex(cellX + 1, cellY + 1));

		if (half == 0) {
			set(0, lowX, lowY, atLow);
			set(3, highX, lowY, atHighX);
			set(6, lowX, highY, atHighY);
		} else {
			set(0, highX, lowY, atHighX);
			set(3, highX, highY, atBoth);
			set(6, lowX, highY, atHighY);
		}
	}

	private void set(int at, float x, float y, float z) {
		corners[at] = x;
		corners[at + 1] = y;
		corners[at + 2] = z;
	}

	private ByteBuffer runsOf(int cellX, int cellY) {
		return world.getCollision().runsOf(sectorOf(cellX, cellY), subCellOf(cellX, cellY));
	}

	private int sectorOf(int cellX, int cellY) {
		return world.getCollision().sectorIndex(cellX, cellY);
	}

	private static int subCellOf(int cellX, int cellY) {
		return CollisionGrid.subCellIndex(cellX, cellY);
	}

	/** Answers whether a cell is inside the world, corners included. */
	private boolean holds(int cellX, int cellY) {
		HeightMap terrain = world.getTerrain();
		return cellX >= 0 && cellY >= 0 && cellX < terrain.getCols() && cellY < terrain.getRows()
				&& sectorOf(cellX, cellY) < world.getCollision().getSectorCount();
	}

	/** Answers the cell a world coordinate falls in, rounding down for both signs. */
	private static int cellOf(float coordinate) {
		return (int) Math.floor(coordinate / HeightMap.CELL_SIZE);
	}

	/** Rounds a height down to the step the run keys are kept in. */
	private static int quantise(float height) {
		return (int) (height / HeightMap.CELL_SIZE) * HeightMap.CELL_SIZE;
	}

	/** Answers whichever of two hits comes first, either of which may be no hit. */
	private static float nearer(float best, float found) {
		if (!RayTriangle.hit(found) || found < 0.0f) {
			return best;
		}
		if (!RayTriangle.hit(best)) {
			return found;
		}
		return found < best ? found : best;
	}
}
