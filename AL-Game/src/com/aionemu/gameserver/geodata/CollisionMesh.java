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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the triangles a world's collision is built from, and the objects whose
 * collision can change shape.
 * <p>
 * The file the client ships is called MeshMaterial.Dat and its two counts read
 * as material and group counts, which is what the reference calls them. They
 * are not: the first counts vertices, three floats apiece, and the second
 * counts triangles. Reading the twelve bytes of an entry as three floats gives
 * clean world coordinates, and the triangles they index close into quads and
 * walls. The collision properties of a triangle come from its material byte
 * here, read against a separate table, not from these entries.
 * <p>
 * A triangle names its first vertex by an offset into the float array and its
 * other two by offsets relative to that, packed into one word as two signed
 * shorts.
 *
 * @author Oraion
 */
public final class CollisionMesh {

	/** The file a world's collision mesh is read from. */
	public static final String FILE_NAME = "MeshMaterial.Dat";

	/** How many floats one vertex runs to. */
	public static final int VERTEX_FLOATS = 3;

	/** How many bytes one vertex runs to. */
	public static final int VERTEX_STRIDE = VERTEX_FLOATS * 4;

	/** How many bytes one triangle runs to: a first vertex and two offsets from it. */
	public static final int TRIANGLE_STRIDE = 8;

	private final GeoVersion version;
	private final float[] vertices;
	private final int[] triangles;
	private final byte[] materials;
	private final List<FieldObject> fieldObjects;

	private CollisionMesh(GeoVersion version, float[] vertices, int[] triangles, byte[] materials,
			List<FieldObject> fieldObjects) {
		this.version = version;
		this.vertices = vertices;
		this.triangles = triangles;
		this.materials = materials;
		this.fieldObjects = Collections.unmodifiableList(fieldObjects);
	}

	/**
	 * Reads the collision mesh of one world.
	 *
	 * @param worldDirectory the world's geodata directory
	 * @return the mesh, never null
	 * @throws IOException if the file is missing or does not read as expected
	 */
	public static CollisionMesh load(Path worldDirectory) throws IOException {
		Path file = worldDirectory.resolve(FILE_NAME);
		ByteBuffer buffer = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
		GeoVersion version = GeoVersion.readHeader(buffer);

		int vertexCount = buffer.getInt();
		checkRoom(buffer, vertexCount, VERTEX_STRIDE, file, "vertices");
		float[] vertices = new float[vertexCount * VERTEX_FLOATS];
		buffer.asFloatBuffer().get(vertices);
		buffer.position(buffer.position() + vertexCount * VERTEX_STRIDE);

		// A world with no vertex carries nothing behind them, not even a count.
		if (vertexCount == 0) {
			return new CollisionMesh(version, vertices, new int[0], new byte[0], new ArrayList<FieldObject>());
		}

		int triangleCount = buffer.getInt();
		checkRoom(buffer, triangleCount, TRIANGLE_STRIDE, file, "triangles");
		int[] triangles = new int[triangleCount * 2];
		buffer.asIntBuffer().get(triangles);
		buffer.position(buffer.position() + triangleCount * TRIANGLE_STRIDE);

		checkRoom(buffer, triangleCount, 1, file, "triangle materials");
		byte[] materials = new byte[triangleCount];
		buffer.get(materials);

		List<FieldObject> fieldObjects = readFieldObjects(buffer, file);
		if (buffer.hasRemaining()) {
			throw new IOException("Left " + buffer.remaining() + " bytes unread at the end of " + file + ".");
		}

		return new CollisionMesh(version, vertices, triangles, materials, fieldObjects);
	}

	private static void checkRoom(ByteBuffer buffer, int count, int stride, Path file, String what)
			throws IOException {
		if (count < 0 || (long) count * stride > buffer.remaining()) {
			throw new IOException("Read a count of " + count + " " + what + " from " + file
					+ ", which the file is too short to hold.");
		}
	}

	private static List<FieldObject> readFieldObjects(ByteBuffer buffer, Path file) throws IOException {
		List<FieldObject> objects = new ArrayList<FieldObject>();
		if (!buffer.hasRemaining()) {
			return objects;
		}

		int count = buffer.getInt();
		if (count < 0) {
			throw new IOException("Read a negative shape-changing object count from " + file + ".");
		}

		for (int i = 0; i < count; i++) {
			int editorId = buffer.getInt();
			String className = readString(buffer, file);
			String name = readString(buffer, file);
			int payloadSize = buffer.getInt();
			if (payloadSize < 0 || payloadSize > buffer.remaining()) {
				throw new IOException("Read a payload of " + payloadSize + " bytes for object " + editorId + " in "
						+ file + ", which the file is too short to hold.");
			}
			byte[] payload = new byte[payloadSize];
			buffer.get(payload);
			objects.add(new FieldObject(editorId, className, name, payload));
		}

		return objects;
	}

	/** Reads one of the two names a shape-changing object carries, up to its terminating zero. */
	private static String readString(ByteBuffer buffer, Path file) throws IOException {
		int start = buffer.position();
		while (buffer.hasRemaining()) {
			if (buffer.get() == 0) {
				int length = buffer.position() - start - 1;
				byte[] bytes = new byte[length];
				buffer.position(start);
				buffer.get(bytes);
				buffer.get();
				return new String(bytes, StandardCharsets.US_ASCII);
			}
		}
		throw new IOException("Ran off the end of " + file + " reading a name.");
	}

	public GeoVersion getVersion() {
		return version;
	}

	public int getVertexCount() {
		return vertices.length / VERTEX_FLOATS;
	}

	public int getTriangleCount() {
		return materials.length;
	}

	/** Answers the vertex coordinates, three floats a vertex, laid end to end. */
	public float[] getVertices() {
		return vertices;
	}

	/** Answers which material a triangle is made of. */
	public int materialOf(int triangle) {
		return materials[triangle] & 0xff;
	}

	/** Answers where a triangle's first vertex starts in the float array. */
	public int firstVertexOf(int triangle) {
		return triangles[triangle * 2];
	}

	/** Answers where a triangle's second vertex starts in the float array. */
	public int secondVertexOf(int triangle) {
		return triangles[triangle * 2] + (short) triangles[triangle * 2 + 1];
	}

	/** Answers where a triangle's third vertex starts in the float array. */
	public int thirdVertexOf(int triangle) {
		return triangles[triangle * 2] + (short) (triangles[triangle * 2 + 1] >> 16);
	}

	/**
	 * Copies a triangle's three corners out.
	 *
	 * @param triangle which triangle
	 * @param corners  filled with x, y and z of each corner in turn, nine floats
	 */
	public void cornersOf(int triangle, float[] corners) {
		System.arraycopy(vertices, firstVertexOf(triangle), corners, 0, VERTEX_FLOATS);
		System.arraycopy(vertices, secondVertexOf(triangle), corners, 3, VERTEX_FLOATS);
		System.arraycopy(vertices, thirdVertexOf(triangle), corners, 6, VERTEX_FLOATS);
	}

	/** Answers the objects whose collision can change shape, doors among them. */
	public List<FieldObject> getFieldObjects() {
		return fieldObjects;
	}
}
