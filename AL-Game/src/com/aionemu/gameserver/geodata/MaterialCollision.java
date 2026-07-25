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
 * Tells what a triangle blocks, from the material it is made of.
 * <p>
 * The original holds this as a table of two hundred materials by six columns,
 * where a zero means the triangle blocks that column and a one means it lets it
 * through. Only the first seventeen rows are compiled into the client; the rest
 * are filled in when it starts, which is why reading the binary alone shows a
 * table that looks mostly empty. It is not empty, it is defaulted:
 * <ul>
 * <li>materials 0 to 16 are baked, and hold every exception there is;</li>
 * <li>materials 121 to 124 come from an obstacle level of one to four, which
 * clears columns 1 to L and sets columns L+1 to 5, leaving column 0 alone;</li>
 * <li>every other material blocks columns 0 to 4 and passes column 5.</li>
 * </ul>
 * So wood, stone, grass, water and lava all block, and that is correct. Nothing
 * is made walkable by its material here. Swimming, drowning and lava damage are
 * decided elsewhere, from the material's identity rather than from this table.
 * <p>
 * Column 0 is movement and creature line of sight. Columns 1 to 4 are
 * projectile and spell line of sight, by obstacle level.
 *
 * @author Oraion
 */
public final class MaterialCollision {

	/** How many materials the table holds. */
	public static final int MATERIAL_COUNT = 200;

	/** How many columns a material is tested against. */
	public static final int COLUMN_COUNT = 6;

	/** The column movement and creature line of sight are tested against. */
	public static final int COLUMN_MOVEMENT = 0;

	/** The first column projectile and spell line of sight are tested against. */
	public static final int COLUMN_PROJECTILE_FIRST = 1;

	/** The last column projectile and spell line of sight are tested against. */
	public static final int COLUMN_PROJECTILE_LAST = 4;

	/**
	 * Holds the seventeen rows the client is compiled with, column 0 first. Every
	 * material that lets anything through at all is in here.
	 */
	private static final byte[][] BAKED = {
			// c0 c1 c2 c3 c4 c5
			{ 0, 0, 0, 0, 0, 1 }, // 0 mat_default
			{ 0, 1, 1, 1, 1, 1 }, // 1 mat_nowalk_obstacle0
			{ 0, 0, 1, 1, 1, 1 }, // 2 mat_nowalk_obstacle1
			{ 0, 0, 0, 1, 1, 1 }, // 3 mat_nowalk_obstacle2
			{ 0, 0, 0, 0, 1, 1 }, // 4 mat_nowalk_obstacle3
			{ 0, 0, 0, 0, 0, 1 }, // 5 mat_nowalk_obstacle4
			{ 1, 0, 1, 1, 1, 1 }, // 6 mat_walk_obstacle1
			{ 1, 0, 0, 1, 1, 1 }, // 7 mat_walk_obstacle2
			{ 1, 0, 0, 0, 1, 1 }, // 8 mat_walk_obstacle3
			{ 1, 0, 0, 0, 0, 1 }, // 9 mat_walk_obstacle4
			{ 1, 0, 0, 0, 0, 0 }, // 10 mat_nobreathing
			{ 1, 1, 1, 1, 1, 1 }, // 11 mat_abyss_castle_shield
			{ 1, 0, 0, 0, 0, 0 }, // 12 mat_lava
			{ 1, 1, 1, 1, 1, 1 }, // 13 mat_passby_dmg_shield
			{ 1, 1, 1, 1, 1, 1 }, // 14 mat_ab1_light_start
			{ 1, 1, 1, 1, 1, 1 }, // 15 mat_ab1_dark_start
			{ 1, 1, 1, 1, 1, 1 }, // 16 mat_ab1_flamemoon
	};

	/** Names the first material an obstacle level is declared for. */
	private static final int FIRST_OBSTACLE_MATERIAL = 121;

	/** Names the last material an obstacle level is declared for. */
	private static final int LAST_OBSTACLE_MATERIAL = 124;

	/**
	 * Names the materials, as far as they are declared. Materials past the end,
	 * and the three the client leaves unnamed, answer null.
	 */
	private static final String[] NAMES = {
			"mat_default", // 0
			"mat_nowalk_obstacle0", // 1
			"mat_nowalk_obstacle1", // 2
			"mat_nowalk_obstacle2", // 3
			"mat_nowalk_obstacle3", // 4
			"mat_nowalk_obstacle4", // 5
			"mat_walk_obstacle1", // 6
			"mat_walk_obstacle2", // 7
			"mat_walk_obstacle3", // 8
			"mat_walk_obstacle4", // 9
			"mat_nobreathing", // 10
			"mat_abyss_castle_shield", // 11
			"mat_lava", // 12
			"mat_passby_dmg_shield", // 13
			"mat_ab1_light_start", // 14
			"mat_ab1_dark_start", // 15
			"mat_ab1_flamemoon", // 16
			null, // 17
			null, // 18
			null, // 19
			"mat_grass", // 20
			"mat_sand", // 21
			"mat_dirt", // 22
			"mat_pavement", // 23
			"mat_wood", // 24
			"mat_stone_tough", // 25
			"mat_stone_marble", // 26
			"mat_pebble", // 27
			"mat_metal_plate", // 28
			"mat_metal_wirenet", // 29
			"mat_fabric", // 30
			"mat_leaves", // 31
			"mat_water", // 32
			"mat_water_deep", // 33
			"mat_magic_circle", // 34
			"mat_flesh", // 35
			"mat_sand_wet", // 36
			"mat_under_water", // 37
			"mat_mob_insect", // 38
			"mat_mob_reptile", // 39
			"mat_mob_rotten", // 40
			"mat_mob_hard", // 41
			"mat_mob_wood", // 42
			"mat_mob_orc", // 43
			"mat_mob_boss", // 44
			"mat_e3item_book", // 45
			"mat_item_wood", // 46
			"foot_2leg_small", // 47
			"foot_2leg_medium", // 48
			"foot_2leg_big", // 49
			"foot_4leg_small", // 50
			"foot_4leg_medium", // 51
			"foot_4leg_big", // 52
			"foot_reptile_small", // 53
			"foot_reptile_medium", // 54
			"foot_reptile_big", // 55
			"foot_flying", // 56
			"foot_insect", // 57
			"foot_etc", // 58
			"mat_snow", // 59
			"mat_fire", // 60
			"mat_cond_fire", // 61
			"mat_weather_cond_fire", // 62
			"mat_time_cond_fire", // 63
			"mat_sword_s", // 64
			"mat_sword_m", // 65
			"mat_sword_h", // 66
			"mat_mace_s", // 67
			"mat_mace_m", // 68
			"mat_mace_h", // 69
			"mat_dagger_s", // 70
			"mat_dagger_m", // 71
			"mat_dagger_h", // 72
			"mat_orb_s", // 73
			"mat_orb_m", // 74
			"mat_orb_h", // 75
			"mat_book_s", // 76
			"mat_book_m", // 77
			"mat_book_h", // 78
			"mat_2hsword_s", // 79
			"mat_2hsword_m", // 80
			"mat_2hsword_h", // 81
			"mat_polearm_s", // 82
			"mat_polearm_m", // 83
			"mat_polearm_h", // 84
			"mat_staff_s", // 85
			"mat_staff_m", // 86
			"mat_staff_h", // 87
			"mat_bow_s", // 88
			"mat_bow_m", // 89
			"mat_bow_h", // 90
			"mat_hp_regen", // 91
			"mat_poison_recovery_a", // 92
			"mat_poison_recovery_b", // 93
			"mat_gold", // 94
			"mat_deep_sand", // 95
			"mat_swamp", // 96
			"mat_strong_lava", // 97
			"foot_2leg_shulack", // 98
			"mat_Test_Material1", // 99
			"mat_Test_Material2", // 100
			"mat_Test_Material3", // 101
			"mat_poison", // 102
			"mat_Medium_lava", // 103
			"mat_housing_type1", // 104
			"mat_housing_type2", // 105
			"mat_housing_type3", // 106
			"mat_drana", // 107
			"mat_acidheal", // 108
			"foot_2leg_Pet", // 109
			"foot_4leg_Pet", // 110
			"mat_Swamp_Arena", // 111
			"mat_mud_Arena", // 112
			"mat_water_damage_arena", // 113
			"mat_housing_spa", // 114
			"mat_dispel_corn", // 115
			"mat_dispel_starturtle", // 116
			"mat_dispel_starfish", // 117
			"mat_must_die", // 118
			"mat_rainwater_Arena", // 119
			"mat_oditonite_Arena", // 120
			"mat_default_obstacle_1", // 121
			"mat_default_obstacle_2", // 122
			"mat_default_obstacle_3", // 123
			"mat_default_obstacle_4", // 124
			"mat_id_01", // 125
			"mat_id_02", // 126
			"mat_id_03", // 127
			"foot_drakan_F_heel", // 128
			"foot_drakan_M_boots", // 129
			"foot_drakan_bare", // 130
			"foot_robot", // 131
			"mat_rainwater_6vs6Boss", // 132
			"foot_npc", // 133
			"mat_Medium_lava_LDF5", // 134
			"mat_ab1_buildup_op_light", // 135
			"mat_ab1_buildup_op_dark", // 136
			"mat_mob_pd", // 137
			"mat_mob_md", // 138
			"mat_poison_dmg1", // 139
			"mat_poison_die", // 140
			"mat_eresukigal_dmg", // 141
	};

	/** Holds one material a byte, a set bit meaning that column is let through. */
	private static final byte[] TABLE = build();

	private MaterialCollision() {
	}

	private static byte[] build() {
		byte[] table = new byte[MATERIAL_COUNT];

		// Start every material where the client's own initialisation starts it:
		// blocking columns 0 to 4, passing column 5.
		byte fallback = (byte) (1 << (COLUMN_COUNT - 1));
		for (int material = 0; material < table.length; material++) {
			table[material] = fallback;
		}

		for (int material = 0; material < BAKED.length; material++) {
			byte columns = 0;
			for (int column = 0; column < COLUMN_COUNT; column++) {
				if (BAKED[material][column] != 0) {
					columns |= (byte) (1 << column);
				}
			}
			table[material] = columns;
		}

		// An obstacle level of L blocks columns 1 to L and passes the rest above
		// column 0, which it never touches.
		for (int material = FIRST_OBSTACLE_MATERIAL; material <= LAST_OBSTACLE_MATERIAL; material++) {
			int level = material - FIRST_OBSTACLE_MATERIAL + 1;
			byte columns = 0;
			for (int column = level + 1; column < COLUMN_COUNT; column++) {
				columns |= (byte) (1 << column);
			}
			table[material] = columns;
		}

		return table;
	}

	/**
	 * Answers whether a triangle of this material stops what a column stands for.
	 * <p>
	 * A material the table has no row for blocks everything. The original would
	 * read past the end of its table instead; no world ships such a material.
	 *
	 * @param material the material a triangle is made of
	 * @param column   which of the six columns to test
	 * @return true if the triangle stops it
	 */
	public static boolean blocks(int material, int column) {
		if (column < 0 || column >= COLUMN_COUNT) {
			throw new IllegalArgumentException("There is no collision column " + column + ".");
		}
		if (material < 0 || material >= MATERIAL_COUNT) {
			return true;
		}
		return (TABLE[material] & (1 << column)) == 0;
	}

	/** Answers whether a triangle of this material stops movement and creature line of sight. */
	public static boolean blocksMovement(int material) {
		return blocks(material, COLUMN_MOVEMENT);
	}

	/**
	 * Names a material, where the client names it.
	 *
	 * @param material the material
	 * @return its name, or null where it has none
	 */
	public static String nameOf(int material) {
		if (material < 0 || material >= NAMES.length) {
			return null;
		}
		return NAMES[material];
	}
}
