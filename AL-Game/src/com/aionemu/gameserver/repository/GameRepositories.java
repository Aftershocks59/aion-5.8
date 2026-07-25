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

import javax.sql.DataSource;

import com.aionemu.commons.database.DatabaseFactory;

/**
 * Hands out the game server's repositories, built once over the connection pool.
 * <p>
 * Every accessor is typed and the set is visible in one place, unlike the DAO
 * lookup it replaces, which answered a class with a cast from a map. Each
 * repository still takes its data source by constructor, which is what lets a
 * test hand it another one.
 * <p>
 * Built lazily, because the pool is only open once the server has started. This
 * grows as the remaining DAOs are converted.
 *
 * @author Oraion
 */
public final class GameRepositories {

	private static GameRepositories instance;

	private final PlayerCooldownRepository skillCooldowns;
	private final PlayerCooldownRepository itemCooldowns;
	private final PlayerCooldownRepository craftCooldowns;
	private final PlayerCooldownRepository portalCooldowns;
	private final PlayerCooldownRepository houseObjectCooldowns;
	private final OldNameRepository oldNames;
	private final PlayerVariableRepository playerVariables;
	private final PlayerEmotionRepository playerEmotions;
	private final PlayerMacroRepository playerMacros;
	private final ServerVariableRepository serverVariables;
	private final PlayerLifeStatRepository playerLifeStats;
	private final PlayerSettingsRepository playerSettings;
	private final PlayerEffectRepository playerEffects;
	private final PlayerNpcFactionRepository playerNpcFactions;
	private final PlayerAppearanceRepository playerAppearance;
	private final PlayerBindPointRepository playerBindPoints;
	private final PlayerTitleRepository playerTitles;
	private final PlayerRecipeRepository playerRecipes;
	private final PlayerSocialRepository playerSocial;
	private final PlayerMotionRepository playerMotions;
	private final PlayerSkillSkinRepository playerSkillSkins;
	private final PlayerPasskeyRepository playerPasskeys;
	private final PlayerWardrobeRepository playerWardrobe;
	private final ChallengeTaskRepository challengeTasks;
	private final PlayerPunishmentRepository playerPunishments;
	private final GuideRepository guides;
	private final F2pRepository f2p;
	private final TownRepository towns;
	private final SurveyRepository surveys;
	private final VeteranRewardRepository veteranRewards;

	/**
	 * Builds every repository over one data source.
	 *
	 * @param dataSource the pool they all borrow from
	 */
	public GameRepositories(DataSource dataSource) {
		skillCooldowns = new JdbcPlayerSkillCooldownRepository(dataSource);
		itemCooldowns = new JdbcItemCooldownRepository(dataSource);
		craftCooldowns = new JdbcCraftCooldownRepository(dataSource);
		portalCooldowns = new JdbcPortalCooldownRepository(dataSource);
		houseObjectCooldowns = new JdbcHouseObjectCooldownRepository(dataSource);
		oldNames = new JdbcOldNameRepository(dataSource);
		playerVariables = new JdbcPlayerVariableRepository(dataSource);
		playerEmotions = new JdbcPlayerEmotionRepository(dataSource);
		playerMacros = new JdbcPlayerMacroRepository(dataSource);
		serverVariables = new JdbcServerVariableRepository(dataSource);
		playerLifeStats = new JdbcPlayerLifeStatRepository(dataSource);
		playerSettings = new JdbcPlayerSettingsRepository(dataSource);
		playerEffects = new JdbcPlayerEffectRepository(dataSource);
		playerNpcFactions = new JdbcPlayerNpcFactionRepository(dataSource);
		playerAppearance = new JdbcPlayerAppearanceRepository(dataSource);
		playerBindPoints = new JdbcPlayerBindPointRepository(dataSource);
		playerTitles = new JdbcPlayerTitleRepository(dataSource);
		playerRecipes = new JdbcPlayerRecipeRepository(dataSource);
		playerSocial = new JdbcPlayerSocialRepository(dataSource);
		playerMotions = new JdbcPlayerMotionRepository(dataSource);
		playerSkillSkins = new JdbcPlayerSkillSkinRepository(dataSource);
		playerPasskeys = new JdbcPlayerPasskeyRepository(dataSource);
		playerWardrobe = new JdbcPlayerWardrobeRepository(dataSource);
		challengeTasks = new JdbcChallengeTaskRepository(dataSource);
		playerPunishments = new JdbcPlayerPunishmentRepository(dataSource);
		guides = new JdbcGuideRepository(dataSource);
		f2p = new JdbcF2pRepository(dataSource);
		towns = new JdbcTownRepository(dataSource);
		surveys = new JdbcSurveyRepository(dataSource);
		veteranRewards = new JdbcVeteranRewardRepository(dataSource);
	}

	/** Answers the shared set, building it over the pool on first use. */
	public static synchronized GameRepositories getInstance() {
		if (instance == null) {
			instance = new GameRepositories(DatabaseFactory.getDataSource());
		}
		return instance;
	}

	public static PlayerCooldownRepository skillCooldowns() {
		return getInstance().skillCooldowns;
	}

	public static PlayerCooldownRepository itemCooldowns() {
		return getInstance().itemCooldowns;
	}

	public static PlayerCooldownRepository craftCooldowns() {
		return getInstance().craftCooldowns;
	}

	public static PlayerCooldownRepository portalCooldowns() {
		return getInstance().portalCooldowns;
	}

	public static PlayerCooldownRepository houseObjectCooldowns() {
		return getInstance().houseObjectCooldowns;
	}

	public static OldNameRepository oldNames() {
		return getInstance().oldNames;
	}

	public static PlayerVariableRepository playerVariables() {
		return getInstance().playerVariables;
	}

	public static PlayerEmotionRepository playerEmotions() {
		return getInstance().playerEmotions;
	}

	public static PlayerMacroRepository playerMacros() {
		return getInstance().playerMacros;
	}

	public static ServerVariableRepository serverVariables() {
		return getInstance().serverVariables;
	}

	public static PlayerLifeStatRepository playerLifeStats() {
		return getInstance().playerLifeStats;
	}

	public static PlayerSettingsRepository playerSettings() {
		return getInstance().playerSettings;
	}

	public static PlayerEffectRepository playerEffects() {
		return getInstance().playerEffects;
	}

	public static PlayerNpcFactionRepository playerNpcFactions() {
		return getInstance().playerNpcFactions;
	}

	public static PlayerAppearanceRepository playerAppearance() {
		return getInstance().playerAppearance;
	}

	public static PlayerBindPointRepository playerBindPoints() {
		return getInstance().playerBindPoints;
	}

	public static PlayerTitleRepository playerTitles() {
		return getInstance().playerTitles;
	}

	public static PlayerRecipeRepository playerRecipes() {
		return getInstance().playerRecipes;
	}

	public static PlayerSocialRepository playerSocial() {
		return getInstance().playerSocial;
	}

	public static PlayerMotionRepository playerMotions() {
		return getInstance().playerMotions;
	}

	public static PlayerSkillSkinRepository playerSkillSkins() {
		return getInstance().playerSkillSkins;
	}

	public static PlayerPasskeyRepository playerPasskeys() {
		return getInstance().playerPasskeys;
	}

	public static PlayerWardrobeRepository playerWardrobe() {
		return getInstance().playerWardrobe;
	}

	public static ChallengeTaskRepository challengeTasks() {
		return getInstance().challengeTasks;
	}

	public static PlayerPunishmentRepository playerPunishments() {
		return getInstance().playerPunishments;
	}

	public static GuideRepository guides() {
		return getInstance().guides;
	}

	public static F2pRepository f2p() {
		return getInstance().f2p;
	}

	public static TownRepository towns() {
		return getInstance().towns;
	}

	public static SurveyRepository surveys() {
		return getInstance().surveys;
	}

	public static VeteranRewardRepository veteranRewards() {
		return getInstance().veteranRewards;
	}
}
