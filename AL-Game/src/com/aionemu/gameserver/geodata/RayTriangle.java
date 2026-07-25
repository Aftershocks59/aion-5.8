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
 * Crosses a ray with a triangle.
 * <p>
 * This is the Moller-Trumbore test the original uses, in both the variant that
 * reads a triangle out of the collision mesh and the one that is handed three
 * corners laid flat. A triangle the ray runs along, within
 * {@link #PARALLEL_EPSILON}, is not hit: the original takes the same threshold.
 * <p>
 * The direction is not normalised. It is the whole way from where the ray
 * starts to where it ends, so a hit at {@code t} of one lands exactly on the
 * far end and anything outside zero to one is past one end or the other.
 *
 * @author Oraion
 */
public final class RayTriangle {

	/** How near to parallel a ray and a triangle count as never meeting. */
	public static final float PARALLEL_EPSILON = 0.001f;

	/** Answers that the ray and the triangle do not meet. */
	public static final float MISS = Float.NaN;

	private RayTriangle() {
	}

	/**
	 * Answers how far along a ray it crosses a triangle.
	 *
	 * @param corners nine floats: the x, y and z of each corner in turn
	 * @param originX where the ray starts, along X
	 * @param originY where the ray starts, along Y
	 * @param originZ where the ray starts, along Z
	 * @param wayX    the whole way the ray runs, along X
	 * @param wayY    the whole way the ray runs, along Y
	 * @param wayZ    the whole way the ray runs, along Z
	 * @return the fraction of the way at which it crosses, or {@link #MISS}
	 */
	public static float intersect(float[] corners, float originX, float originY, float originZ, float wayX,
			float wayY, float wayZ) {
		return intersect(corners, 0, 3, 6, originX, originY, originZ, wayX, wayY, wayZ);
	}

	/**
	 * Answers how far along a ray it crosses a triangle, whose corners are named
	 * by where they start in an array of coordinates.
	 *
	 * @param vertices the coordinates, three floats a corner
	 * @param first    where the first corner starts
	 * @param second   where the second corner starts
	 * @param third    where the third corner starts
	 * @param originX  where the ray starts, along X
	 * @param originY  where the ray starts, along Y
	 * @param originZ  where the ray starts, along Z
	 * @param wayX     the whole way the ray runs, along X
	 * @param wayY     the whole way the ray runs, along Y
	 * @param wayZ     the whole way the ray runs, along Z
	 * @return the fraction of the way at which it crosses, or {@link #MISS}
	 */
	public static float intersect(float[] vertices, int first, int second, int third, float originX, float originY,
			float originZ, float wayX, float wayY, float wayZ) {
		float ax = vertices[first];
		float ay = vertices[first + 1];
		float az = vertices[first + 2];

		float abX = vertices[second] - ax;
		float abY = vertices[second + 1] - ay;
		float abZ = vertices[second + 2] - az;

		float acX = vertices[third] - ax;
		float acY = vertices[third + 1] - ay;
		float acZ = vertices[third + 2] - az;

		// The way crossed with one edge, which the other edge is measured against.
		float pX = wayY * acZ - wayZ * acY;
		float pY = wayZ * acX - wayX * acZ;
		float pZ = wayX * acY - wayY * acX;

		float determinant = abX * pX + abY * pY + abZ * pZ;
		if (determinant > -PARALLEL_EPSILON && determinant < PARALLEL_EPSILON) {
			return MISS;
		}
		float inverse = 1.0f / determinant;

		float toStartX = originX - ax;
		float toStartY = originY - ay;
		float toStartZ = originZ - az;

		float along = (toStartX * pX + toStartY * pY + toStartZ * pZ) * inverse;
		if (along < 0.0f || along > 1.0f) {
			return MISS;
		}

		float qX = toStartY * abZ - toStartZ * abY;
		float qY = toStartZ * abX - toStartX * abZ;
		float qZ = toStartX * abY - toStartY * abX;

		float across = (wayX * qX + wayY * qY + wayZ * qZ) * inverse;
		if (across < 0.0f || along + across > 1.0f) {
			return MISS;
		}

		return (acX * qX + acY * qY + acZ * qZ) * inverse;
	}

	/** Answers whether a result of {@link #intersect} is a hit at all. */
	public static boolean hit(float distance) {
		return !Float.isNaN(distance);
	}
}
