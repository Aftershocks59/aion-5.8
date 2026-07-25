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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the material table against what the client is known to hold.
 *
 * @author Oraion
 */
class MaterialCollisionTest {

	/** Names the only materials that let movement through, out of all two hundred. */
	private static final Set<Integer> WALKABLE = new HashSet<Integer>(
			Arrays.asList(6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16));

	@Test
	@DisplayName("Blocks every column but the last for an undeclared material")
	void defaultsToSolid() {
		// 17 is the first material past the baked rows, 199 the last of the table,
		// and the four between them are the ones the worlds are mostly built of.
		for (int material : new int[] { 17, 20, 24, 25, 26, 141, 142, 199 }) {
			for (int column = 0; column <= 4; column++) {
				assertTrue(MaterialCollision.blocks(material, column),
						"material " + material + " should block column " + column);
			}
			assertFalse(MaterialCollision.blocks(material, 5), "material " + material + " should pass column 5");
		}
	}

	@Test
	@DisplayName("Derives the obstacle rows the way the client's table holds them")
	void obstacleLevelsMatchTheTable() {
		// The rows come from an obstacle level of one to four, applied by a rule.
		// These are the values that rule has to land on.
		byte[][] expected = {
				{ 0, 0, 1, 1, 1, 1 }, // 121 mat_default_obstacle_1
				{ 0, 0, 0, 1, 1, 1 }, // 122 mat_default_obstacle_2
				{ 0, 0, 0, 0, 1, 1 }, // 123 mat_default_obstacle_3
				{ 0, 0, 0, 0, 0, 1 }, // 124 mat_default_obstacle_4
		};
		for (int row = 0; row < expected.length; row++) {
			int material = 121 + row;
			for (int column = 0; column < MaterialCollision.COLUMN_COUNT; column++) {
				assertEquals(expected[row][column] == 0, MaterialCollision.blocks(material, column),
						"material " + material + ", column " + column);
			}
		}
	}

	@Test
	@DisplayName("Lets nothing through for the shields and the abyss starts")
	void shieldsPassEverything() {
		for (int material : new int[] { 11, 13, 14, 15, 16 }) {
			for (int column = 0; column < MaterialCollision.COLUMN_COUNT; column++) {
				assertFalse(MaterialCollision.blocks(material, column),
						"material " + material + " should pass column " + column);
			}
		}
	}

	@Test
	@DisplayName("Lets lava and the airless material be walked on but not shot through")
	void lavaPassesMovementAlone() {
		for (int material : new int[] { 10, 12 }) {
			assertFalse(MaterialCollision.blocksMovement(material));
			for (int column = 1; column < MaterialCollision.COLUMN_COUNT; column++) {
				assertTrue(MaterialCollision.blocks(material, column),
						"material " + material + " should block column " + column);
			}
		}
	}

	@Test
	@DisplayName("Grades the walk-through obstacles by level")
	void walkObstaclesAreGraded() {
		// Materials 6 to 9 all pass movement, and each blocks one more projectile
		// column than the last.
		for (int level = 1; level <= 4; level++) {
			int material = 5 + level;
			assertFalse(MaterialCollision.blocksMovement(material), "material " + material);
			for (int column = 1; column <= 4; column++) {
				assertEquals(column <= level, MaterialCollision.blocks(material, column),
						"material " + material + ", column " + column);
			}
		}
	}

	@Test
	@DisplayName("Stops movement on every material but the eleven baked to pass it")
	void onlyBakedMaterialsAreWalkable() {
		for (int material = 0; material < MaterialCollision.MATERIAL_COUNT; material++) {
			assertEquals(!WALKABLE.contains(material), MaterialCollision.blocksMovement(material),
					"material " + material + " walkability");
		}
	}

	@Test
	@DisplayName("Stops everything for a material the table has no row for")
	void unknownMaterialBlocks() {
		for (int column = 0; column < MaterialCollision.COLUMN_COUNT; column++) {
			assertTrue(MaterialCollision.blocks(MaterialCollision.MATERIAL_COUNT, column));
			assertTrue(MaterialCollision.blocks(-1, column));
		}
	}

	@Test
	@DisplayName("Refuses a column the table does not have")
	void rejectsUnknownColumn() {
		assertThrows(IllegalArgumentException.class, () -> MaterialCollision.blocks(0, -1));
		assertThrows(IllegalArgumentException.class,
				() -> MaterialCollision.blocks(0, MaterialCollision.COLUMN_COUNT));
	}

	@Test
	@DisplayName("Names the materials in step with their ids")
	void namesLineUp() {
		// A name inserted or dropped would shift everything after it, so check
		// both ends and several points between.
		assertEquals("mat_default", MaterialCollision.nameOf(0));
		assertEquals("mat_ab1_flamemoon", MaterialCollision.nameOf(16));
		assertEquals("mat_grass", MaterialCollision.nameOf(20));
		assertEquals("mat_wood", MaterialCollision.nameOf(24));
		assertEquals("foot_2leg_small", MaterialCollision.nameOf(47));
		assertEquals("mat_sword_s", MaterialCollision.nameOf(64));
		assertEquals("foot_2leg_shulack", MaterialCollision.nameOf(98));
		assertEquals("mat_default_obstacle_1", MaterialCollision.nameOf(121));
		assertEquals("mat_default_obstacle_4", MaterialCollision.nameOf(124));
		assertEquals("mat_eresukigal_dmg", MaterialCollision.nameOf(141));

		// The client leaves three of them unnamed, and names nothing past 141.
		assertNull(MaterialCollision.nameOf(17));
		assertNull(MaterialCollision.nameOf(19));
		assertNull(MaterialCollision.nameOf(142));
		assertNull(MaterialCollision.nameOf(199));
	}
}
