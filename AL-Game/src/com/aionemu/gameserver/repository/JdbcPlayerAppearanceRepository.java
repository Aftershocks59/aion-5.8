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
package com.aionemu.gameserver.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

import javax.sql.DataSource;

import com.aionemu.commons.database.JdbcRepositorySupport;
import com.aionemu.commons.database.RepositoryException;
import com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance;

/**
 * Reads and writes what a character looks like, over JDBC.
 * <p>
 * Sixty-two sliders, each its own column. The DAO spelled the column list, the
 * question marks and the sixty-two binds out three separate times, in three
 * places that had to stay in the same order. Here each slider is declared once,
 * with the column it lives in and how to read and write it, and the statements
 * are built from that list, so the order cannot drift.
 *
 * @author Oraion
 */
public final class JdbcPlayerAppearanceRepository extends JdbcRepositorySupport
		implements PlayerAppearanceRepository {

	/** Ties a column to the two ways of moving one slider. */
	private static final class Slider {

		final String column;
		final ToIntFunction<PlayerAppearance> reader;
		final ObjIntConsumer<PlayerAppearance> writer;

		Slider(String column, ToIntFunction<PlayerAppearance> reader, ObjIntConsumer<PlayerAppearance> writer) {
			this.column = column;
			this.reader = reader;
			this.writer = writer;
		}
	}

	private static final List<Slider> SLIDERS = sliders();

	private static final String SELECT_ONE = buildSelect();
	private static final String REPLACE_ONE = buildReplace();

	public JdbcPlayerAppearanceRepository(DataSource dataSource) {
		super(dataSource);
	}

	@Override
	public PlayerAppearance find(int playerId) {
		PlayerAppearance appearance = new PlayerAppearance();
		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(SELECT_ONE)) {
			statement.setInt(1, playerId);
			try (ResultSet rows = statement.executeQuery()) {
				if (rows.next()) {
					for (Slider slider : SLIDERS) {
						slider.writer.accept(appearance, rows.getInt(slider.column));
					}
					appearance.setHeight(rows.getFloat("height"));
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Failed to read the appearance of character " + playerId + ".", e);
		}
		return appearance;
	}

	@Override
	public boolean save(int playerId, PlayerAppearance appearance) {
		if (appearance == null) {
			throw new IllegalArgumentException("Cannot store a null appearance.");
		}

		try (Connection connection = connection();
				PreparedStatement statement = connection.prepareStatement(REPLACE_ONE)) {
			int index = 1;
			statement.setInt(index++, playerId);
			for (Slider slider : SLIDERS) {
				statement.setInt(index++, slider.reader.applyAsInt(appearance));
			}
			statement.setFloat(index, appearance.getHeight());
			return statement.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RepositoryException("Failed to write the appearance of character " + playerId + ".", e);
		}
	}

	private static String buildSelect() {
		StringBuilder sql = new StringBuilder("SELECT ");
		for (Slider slider : SLIDERS) {
			sql.append('`').append(slider.column).append("`,");
		}
		return sql.append("`height` FROM `player_appearance` WHERE `player_id` = ?").toString();
	}

	private static String buildReplace() {
		StringBuilder columns = new StringBuilder("`player_id`,");
		StringBuilder places = new StringBuilder("?,");
		for (Slider slider : SLIDERS) {
			columns.append('`').append(slider.column).append("`,");
			places.append("?,");
		}
		columns.append("`height`");
		places.append('?');
		return "REPLACE INTO `player_appearance` (" + columns + ") VALUES (" + places + ")";
	}

	/** Declares every slider once, in the order the table holds them. */
	private static List<Slider> sliders() {
		List<Slider> sliders = new ArrayList<Slider>();
		sliders.add(new Slider("voice", PlayerAppearance::getVoice, PlayerAppearance::setVoice));
		sliders.add(new Slider("skin_rgb", PlayerAppearance::getSkinRGB, PlayerAppearance::setSkinRGB));
		sliders.add(new Slider("hair_rgb", PlayerAppearance::getHairRGB, PlayerAppearance::setHairRGB));
		sliders.add(new Slider("eye_rgb", PlayerAppearance::getEyeRGB, PlayerAppearance::setEyeRGB));
		sliders.add(new Slider("lip_rgb", PlayerAppearance::getLipRGB, PlayerAppearance::setLipRGB));
		sliders.add(new Slider("face", PlayerAppearance::getFace, PlayerAppearance::setFace));
		sliders.add(new Slider("hair", PlayerAppearance::getHair, PlayerAppearance::setHair));
		sliders.add(new Slider("deco", PlayerAppearance::getDeco, PlayerAppearance::setDeco));
		sliders.add(new Slider("tattoo", PlayerAppearance::getTattoo, PlayerAppearance::setTattoo));
		sliders.add(new Slider("face_contour", PlayerAppearance::getFaceContour, PlayerAppearance::setFaceContour));
		sliders.add(new Slider("expression", PlayerAppearance::getExpression, PlayerAppearance::setExpression));
		sliders.add(new Slider("pupil_shape", PlayerAppearance::getPupilShape, PlayerAppearance::setPupilShape));
		sliders.add(new Slider("remove_mane", PlayerAppearance::getRemoveMane, PlayerAppearance::setRemoveMane));
		sliders.add(new Slider("right_eye_rgb", PlayerAppearance::getRightEyeRGB, PlayerAppearance::setRightEyeRGB));
		sliders.add(new Slider("eye_lash_shape", PlayerAppearance::getEyeLashShape, PlayerAppearance::setEyeLashShape));
		sliders.add(new Slider("jaw_line", PlayerAppearance::getJawLine, PlayerAppearance::setJawLine));
		sliders.add(new Slider("forehead", PlayerAppearance::getForehead, PlayerAppearance::setForehead));
		sliders.add(new Slider("eye_height", PlayerAppearance::getEyeHeight, PlayerAppearance::setEyeHeight));
		sliders.add(new Slider("eye_space", PlayerAppearance::getEyeSpace, PlayerAppearance::setEyeSpace));
		sliders.add(new Slider("eye_width", PlayerAppearance::getEyeWidth, PlayerAppearance::setEyeWidth));
		sliders.add(new Slider("eye_size", PlayerAppearance::getEyeSize, PlayerAppearance::setEyeSize));
		sliders.add(new Slider("eye_shape", PlayerAppearance::getEyeShape, PlayerAppearance::setEyeShape));
		sliders.add(new Slider("eye_angle", PlayerAppearance::getEyeAngle, PlayerAppearance::setEyeAngle));
		sliders.add(new Slider("brow_height", PlayerAppearance::getBrowHeight, PlayerAppearance::setBrowHeight));
		sliders.add(new Slider("brow_angle", PlayerAppearance::getBrowAngle, PlayerAppearance::setBrowAngle));
		sliders.add(new Slider("brow_shape", PlayerAppearance::getBrowShape, PlayerAppearance::setBrowShape));
		sliders.add(new Slider("nose", PlayerAppearance::getNose, PlayerAppearance::setNose));
		sliders.add(new Slider("nose_bridge", PlayerAppearance::getNoseBridge, PlayerAppearance::setNoseBridge));
		sliders.add(new Slider("nose_width", PlayerAppearance::getNoseWidth, PlayerAppearance::setNoseWidth));
		sliders.add(new Slider("nose_tip", PlayerAppearance::getNoseTip, PlayerAppearance::setNoseTip));
		sliders.add(new Slider("cheek", PlayerAppearance::getCheek, PlayerAppearance::setCheek));
		sliders.add(new Slider("lip_height", PlayerAppearance::getLipHeight, PlayerAppearance::setLipHeight));
		sliders.add(new Slider("mouth_size", PlayerAppearance::getMouthSize, PlayerAppearance::setMouthSize));
		sliders.add(new Slider("lip_size", PlayerAppearance::getLipSize, PlayerAppearance::setLipSize));
		sliders.add(new Slider("smile", PlayerAppearance::getSmile, PlayerAppearance::setSmile));
		sliders.add(new Slider("lip_shape", PlayerAppearance::getLipShape, PlayerAppearance::setLipShape));
		sliders.add(new Slider("jaw_height", PlayerAppearance::getJawHeigh, PlayerAppearance::setJawHeigh));
		sliders.add(new Slider("chin_jut", PlayerAppearance::getChinJut, PlayerAppearance::setChinJut));
		sliders.add(new Slider("ear_shape", PlayerAppearance::getEarShape, PlayerAppearance::setEarShape));
		sliders.add(new Slider("head_size", PlayerAppearance::getHeadSize, PlayerAppearance::setHeadSize));
		sliders.add(new Slider("neck", PlayerAppearance::getNeck, PlayerAppearance::setNeck));
		sliders.add(new Slider("neck_length", PlayerAppearance::getNeckLength, PlayerAppearance::setNeckLength));
		sliders.add(new Slider("shoulder_size", PlayerAppearance::getShoulderSize, PlayerAppearance::setShoulderSize));
		sliders.add(new Slider("torso", PlayerAppearance::getTorso, PlayerAppearance::setTorso));
		sliders.add(new Slider("chest", PlayerAppearance::getChest, PlayerAppearance::setChest));
		sliders.add(new Slider("waist", PlayerAppearance::getWaist, PlayerAppearance::setWaist));
		sliders.add(new Slider("hips", PlayerAppearance::getHips, PlayerAppearance::setHips));
		sliders.add(new Slider("arm_thickness", PlayerAppearance::getArmThickness, PlayerAppearance::setArmThickness));
		sliders.add(new Slider("hand_size", PlayerAppearance::getHandSize, PlayerAppearance::setHandSize));
		sliders.add(new Slider("leg_thickness", PlayerAppearance::getLegThickness, PlayerAppearance::setLegThickness));
		sliders.add(new Slider("facial_rate", PlayerAppearance::getFacialRate, PlayerAppearance::setFacialRate));
		sliders.add(new Slider("foot_size", PlayerAppearance::getFootSize, PlayerAppearance::setFootSize));
		sliders.add(new Slider("arm_length", PlayerAppearance::getArmLength, PlayerAppearance::setArmLength));
		sliders.add(new Slider("leg_length", PlayerAppearance::getLegLength, PlayerAppearance::setLegLength));
		sliders.add(new Slider("shoulders", PlayerAppearance::getShoulders, PlayerAppearance::setShoulders));
		sliders.add(new Slider("face_shape", PlayerAppearance::getFaceShape, PlayerAppearance::setFaceShape));
		sliders.add(new Slider("pupil_size", PlayerAppearance::getPupilSize, PlayerAppearance::setPupilSize));
		sliders.add(new Slider("upper_torso", PlayerAppearance::getUpperTorso, PlayerAppearance::setUpperTorso));
		sliders.add(new Slider("fore_arm_thickness", PlayerAppearance::getForeArmThickness,
				PlayerAppearance::setForeArmThickness));
		sliders.add(new Slider("hand_span", PlayerAppearance::getHandSpan, PlayerAppearance::setHandSpan));
		sliders.add(new Slider("calf_thickness", PlayerAppearance::getCalfThickness,
				PlayerAppearance::setCalfThickness));
		return sliders;
	}
}
