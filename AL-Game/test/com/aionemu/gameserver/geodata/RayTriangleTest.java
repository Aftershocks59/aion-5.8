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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers crossing a ray with a triangle.
 *
 * @author Oraion
 */
class RayTriangleTest {

	private static final float EPSILON = 0.0001f;

	/** A triangle lying flat at a height of ten, covering the corner of the world. */
	private static final float[] FLOOR = { 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 10.0f, 0.0f, 10.0f, 10.0f };

	@Test
	@DisplayName("Crosses a floor halfway down a ray that reaches twice as far")
	void crossesAtTheRightFraction() {
		// From twenty down to nothing, a floor at ten is exactly halfway.
		float at = RayTriangle.intersect(FLOOR, 1.0f, 1.0f, 20.0f, 0.0f, 0.0f, -20.0f);
		assertTrue(RayTriangle.hit(at));
		assertEquals(0.5f, at, EPSILON);
	}

	@Test
	@DisplayName("Misses beside the triangle")
	void missesOutsideTheCorners() {
		// Inside the triangle's square but past its long edge.
		assertFalse(RayTriangle.hit(RayTriangle.intersect(FLOOR, 8.0f, 8.0f, 20.0f, 0.0f, 0.0f, -20.0f)));
		assertFalse(RayTriangle.hit(RayTriangle.intersect(FLOOR, -1.0f, 1.0f, 20.0f, 0.0f, 0.0f, -20.0f)));
	}

	@Test
	@DisplayName("Misses a triangle the ray runs along")
	void missesWhenParallel() {
		assertFalse(RayTriangle.hit(RayTriangle.intersect(FLOOR, 1.0f, 1.0f, 10.0f, 5.0f, 0.0f, 0.0f)));
	}

	@Test
	@DisplayName("Answers past the end of the way for a floor the ray stops short of")
	void answersBeyondTheEnd() {
		// The ray gives up at fifteen, so the floor at ten is a third further on
		// than it goes. Callers bound the fraction; the crossing itself is real.
		float at = RayTriangle.intersect(FLOOR, 1.0f, 1.0f, 20.0f, 0.0f, 0.0f, -5.0f);
		assertTrue(RayTriangle.hit(at));
		assertEquals(2.0f, at, EPSILON);
	}

	@Test
	@DisplayName("Answers behind the start for a floor the ray points away from")
	void answersBehindTheStart() {
		float at = RayTriangle.intersect(FLOOR, 1.0f, 1.0f, 20.0f, 0.0f, 0.0f, 20.0f);
		assertTrue(RayTriangle.hit(at));
		assertTrue(at < 0.0f);
	}

	@Test
	@DisplayName("Crosses a triangle named by where its corners start")
	void readsCornersFromAnArray() {
		// The same floor, with a spare vertex in front of it, reached by offset.
		float[] vertices = { 99.0f, 99.0f, 99.0f, 0.0f, 0.0f, 10.0f, 10.0f, 0.0f, 10.0f, 0.0f, 10.0f, 10.0f };
		float at = RayTriangle.intersect(vertices, 3, 6, 9, 1.0f, 1.0f, 20.0f, 0.0f, 0.0f, -20.0f);
		assertEquals(0.5f, at, EPSILON);
	}
}
