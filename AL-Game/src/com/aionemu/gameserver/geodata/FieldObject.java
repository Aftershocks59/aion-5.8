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

/**
 * Names one object whose collision can change shape, a door above all.
 * <p>
 * The world's materials file carries these after its tables. An object owns no
 * geometry of its own: its payload names a run of triangles in the world's
 * collision mesh, which is where its shape lives. That run stays where it is
 * whatever the object does. What changes is whether the object is counted at
 * all, which is why a door that opens does not have to be taken out of the
 * world and put back.
 * <p>
 * Only doors block. Every class carries a run of triangles, but the original
 * gives real collision to doors alone; the rest answer that nothing is there.
 *
 * @author Oraion
 */
public final class FieldObject {

	/**
	 * How long a record's payload runs. Every one of the 9658 records shipped
	 * across the 213 worlds carries exactly this many bytes, four little-endian
	 * ints in the pattern {@code [a, b, a, b]} where {@code a < b}.
	 */
	public static final int PAYLOAD_SIZE = 16;

	/**
	 * How far outside its own triangles a door's height range reaches.
	 * <p>
	 * The original works the range out once, from the triangles, and widens it
	 * by this much at both ends so a ray grazing the very top or bottom is not
	 * turned away before the triangles get a say.
	 */
	public static final float HEIGHT_MARGIN = 0.001f;

	private final int editorId;
	private final String className;
	private final String name;
	private final byte[] payload;
	private final int firstTriangle;
	private final int lastTriangle;

	private float lowX;
	private float lowY;
	private float lowZ;
	private float highX;
	private float highY;
	private float highZ;
	private boolean measured;

	FieldObject(int editorId, String className, String name, byte[] payload) {
		this.editorId = editorId;
		this.className = className;
		this.name = name;
		this.payload = payload;
		this.firstTriangle = readInt(payload, 0);
		this.lastTriangle = readInt(payload, 4);
	}

	private static int readInt(byte[] bytes, int at) {
		if (bytes.length < at + 4) {
			return -1;
		}
		return (bytes[at] & 0xff) | ((bytes[at + 1] & 0xff) << 8) | ((bytes[at + 2] & 0xff) << 16)
				| ((bytes[at + 3] & 0xff) << 24);
	}

	/** Answers the id the map editor gave this object. */
	public int getEditorId() {
		return editorId;
	}

	/** Answers the object's class, such as Door, AbyssDoor or PlaceableObject. */
	public String getClassName() {
		return className;
	}

	/**
	 * Answers the name the editor gave this object, such as AbyssDoor12.
	 * <p>
	 * The original loader reads this and throws it away; it is kept because it
	 * is the only thing that tells one door from another by eye.
	 */
	public String getName() {
		return name;
	}

	/** Answers the record's payload as it was read. */
	public byte[] getPayload() {
		return payload;
	}

	/** Answers where this object's triangles start in the world's collision mesh. */
	public int getFirstTriangle() {
		return firstTriangle;
	}

	/** Answers where they stop, one past the last of them. */
	public int getLastTriangle() {
		return lastTriangle;
	}

	/** Answers whether this object is one of the classes that block. */
	public boolean isDoor() {
		return className.endsWith("Door");
	}

	/**
	 * Answers whether this object stops anything while it is shut.
	 * <p>
	 * Whether it is shut is not asked here. One world is read once and every
	 * instance of it shares these records, while a door in one instance opens
	 * without opening in the next, so which doors stand open belongs to the
	 * instance and is kept there.
	 */
	public boolean blocksWhenShut() {
		return measured && isDoor();
	}

	/** Answers whether this object's triangles were found and measured. */
	public boolean isMeasured() {
		return measured;
	}

	/** Answers the lowest height this object reaches, with the margin the original adds. */
	public float getLowZ() {
		return lowZ;
	}

	/** Answers the highest height it reaches, likewise. */
	public float getHighZ() {
		return highZ;
	}

	/**
	 * Works out how high this object stands, from the triangles it names.
	 * <p>
	 * An object whose run is empty or reaches outside the mesh is left
	 * unmeasured, and never blocks: there is nothing of it to block with.
	 *
	 * @param mesh the world's collision mesh
	 */
	void measure(CollisionMesh mesh) {
		if (firstTriangle < 0 || lastTriangle <= firstTriangle || lastTriangle > mesh.getTriangleCount()) {
			return;
		}

		float[] vertices = mesh.getVertices();
		float[] low = { Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE };
		float[] high = { -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE };
		for (int triangle = firstTriangle; triangle < lastTriangle; triangle++) {
			stretch(low, high, vertices, mesh.firstVertexOf(triangle));
			stretch(low, high, vertices, mesh.secondVertexOf(triangle));
			stretch(low, high, vertices, mesh.thirdVertexOf(triangle));
		}

		lowX = low[0];
		lowY = low[1];
		highX = high[0];
		highY = high[1];
		lowZ = low[2] - HEIGHT_MARGIN;
		highZ = high[2] + HEIGHT_MARGIN;
		measured = true;
	}

	private static void stretch(float[] low, float[] high, float[] vertices, int vertex) {
		for (int axis = 0; axis < 3; axis++) {
			float at = vertices[vertex + axis];
			low[axis] = Math.min(low[axis], at);
			high[axis] = Math.max(high[axis], at);
		}
	}

	/** Answers whether a point stands inside the box this object occupies. */
	public boolean contains(float x, float y, float z) {
		return measured && x >= lowX && x <= highX && y >= lowY && y <= highY && z >= lowZ && z <= highZ;
	}

	@Override
	public String toString() {
		return className + " " + name + " (editor id " + editorId + ")";
	}
}
