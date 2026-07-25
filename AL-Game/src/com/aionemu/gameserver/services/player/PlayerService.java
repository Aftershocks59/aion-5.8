/*

 *
 *  Encom is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Encom is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser Public License
 *  along with Encom.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.gameserver.services.player;

import com.aionemu.gameserver.repository.GameRepositories;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.commons.utils.GenericValidator;
import com.aionemu.gameserver.configs.main.CacheConfig;
import com.aionemu.gameserver.controllers.FlyController;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.controllers.effect.PlayerEffectController;
import com.aionemu.gameserver.dao.AbyssRankDAO;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.dao.ItemStoneListDAO;
import com.aionemu.gameserver.dao.MailDAO;
import com.aionemu.gameserver.dao.PlayerDAO;
import com.aionemu.gameserver.dao.PlayerEventsWindowDAO;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.PlayerInitialData;
import com.aionemu.gameserver.dataholders.PlayerInitialData.LocationData;
import com.aionemu.gameserver.dataholders.PlayerInitialData.PlayerCreationData;
import com.aionemu.gameserver.dataholders.PlayerInitialData.PlayerCreationData.ItemType;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.account.PlayerAccountData;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Equipment;
import com.aionemu.gameserver.model.gameobjects.player.MacroList;
import com.aionemu.gameserver.model.gameobjects.player.Mailbox;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.model.house.House;
import com.aionemu.gameserver.model.house.HouseRegistry;
import com.aionemu.gameserver.model.house.HouseStatus;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.items.storage.PlayerStorage;
import com.aionemu.gameserver.model.items.storage.Storage;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.skill.PlayerSkillList;
import com.aionemu.gameserver.model.stats.calc.functions.PlayerStatFunctions;
import com.aionemu.gameserver.model.stats.listeners.TitleChangeListener;
import com.aionemu.gameserver.model.team.legion.LegionMember;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.services.LegionService;
import com.aionemu.gameserver.services.PunishmentService.PunishmentType;
import com.aionemu.gameserver.services.SkillLearnService;
import com.aionemu.gameserver.services.events.ShugoSweepService;
import com.aionemu.gameserver.services.item.ItemFactory;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.utils.collections.cachemap.CacheMap;
import com.aionemu.gameserver.utils.collections.cachemap.CacheMapFactory;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.WorldPosition;
import com.aionemu.gameserver.world.knownlist.KnownList;
import com.aionemu.gameserver.world.knownlist.Visitor;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class PlayerService {
	private static final CacheMap<Integer, Player> playerCache = CacheMapFactory.createSoftCacheMap("Player", "player");

	public static boolean isFreeName(String name) {
		return !DAOManager.getDAO(PlayerDAO.class).isNameUsed(name);
	}

	public static boolean isOldName(String name) {
		return GameRepositories.oldNames().wasUsed(name);
	}

	public static boolean storeNewPlayer(Player player, String accountName, int accountId) {
		if (!DAOManager.getDAO(PlayerDAO.class).saveNewPlayer(player.getCommonData(), accountId, accountName)
				|| !GameRepositories.playerAppearance().save(player)) {
			return false;
		}
		GameRepositories.playerSkills().save(player);
		return DAOManager.getDAO(InventoryDAO.class).store(player);
	}

	public static void storePlayer(Player player) {
		DAOManager.getDAO(PlayerDAO.class).storePlayer(player);
		GameRepositories.playerSkills().save(player);
		GameRepositories.equippedStigmas().save(player);
		GameRepositories.playerSettings().save(player);
		GameRepositories.playerQuests().save(player);
		DAOManager.getDAO(AbyssRankDAO.class).storeAbyssRank(player);
		GameRepositories.playerPunishments().save(player, PunishmentType.PRISON);
		GameRepositories.playerPunishments().save(player, PunishmentType.GATHER);
		DAOManager.getDAO(InventoryDAO.class).store(player);

		for (House house : player.getHouses()) {
			GameRepositories.houses().save(house);
			if (house.getRegistry() != null
					&& house.getRegistry().getPersistentState() == PersistentState.UPDATE_REQUIRED) {
				GameRepositories.houseRegistries().save(house.getRegistry(),
						player.getCommonData().getPlayerObjId());
			}
		}

		DAOManager.getDAO(ItemStoneListDAO.class).save(player);
		DAOManager.getDAO(MailDAO.class).storeMailbox(player);
		GameRepositories.portalCooldowns().store(player);
		GameRepositories.craftCooldowns().store(player);
		GameRepositories.playerNpcFactions().store(player);
		GameRepositories.lunaShop().save(player);
		GameRepositories.eventItems().load(player);
		GameRepositories.creativityPoints().save(player);
	}

	public static Player getPlayer(int playerObjId, Account account) {
		Player player = playerCache.get(playerObjId);
		if (player != null) {
			return player;
		}
		PlayerAccountData playerAccountData = account.getPlayerAccountData(playerObjId);
		PlayerCommonData pcd = playerAccountData.getPlayerCommonData();
		PlayerAppearance appearance = playerAccountData.getAppereance();
		player = new Player(new PlayerController(), pcd, appearance, account);
		LegionMember legionMember = LegionService.getInstance().getLegionMember(player.getObjectId());
		if (legionMember != null) {
			player.setLegionMember(legionMember);
		}
		MacroList macroses = GameRepositories.playerMacros().findAll(playerObjId);
		player.setMacroList(macroses);
		player.setSkillList(GameRepositories.playerSkills().load(playerObjId));
		player.setEquipedStigmaList(GameRepositories.equippedStigmas().load(playerObjId));
		player.setKnownlist(new KnownList(player));
		player.setFriendList(GameRepositories.playerSocial().findFriends(player, DAOManager.getDAO(PlayerDAO.class)::loadPlayerCommonData));
		player.setBlockList(GameRepositories.playerSocial().findBlocked(player, DAOManager.getDAO(PlayerDAO.class)::loadPlayerCommonData));
		player.setTitleList(GameRepositories.playerTitles().findAll(playerObjId));
		player.setCP(GameRepositories.creativityPoints().load(player.getObjectId()));
		player.setEventWindow(DAOManager.getDAO(PlayerEventsWindowDAO.class).load(player));
		player.setAtreianBestiary(GameRepositories.atreianBestiary().load(player.getObjectId()));
		player.setWardrobe(GameRepositories.playerWardrobe().findAll(player));
		GameRepositories.f2p().load(player);
		GameRepositories.playerSettings().load(player);
		DAOManager.getDAO(AbyssRankDAO.class).loadAbyssRank(player);
		GameRepositories.playerNpcFactions().load(player);
		GameRepositories.playerMotions().load(player);
		player.setVars(GameRepositories.playerVariables().findAll(player.getObjectId()));
		Equipment equipment = DAOManager.getDAO(InventoryDAO.class).loadEquipment(player);
		ItemService.loadItemStones(equipment.getEquippedItemsWithoutStigma());
		equipment.setOwner(player);
		player.setEquipment(equipment);
		player.setEffectController(new PlayerEffectController(player));
		player.setFlyController(new FlyController(player));
		PlayerStatFunctions.addPredefinedStatFunctions(player);
		player.setQuestStateList(GameRepositories.playerQuests().load(player));
		player.setRecipeList(GameRepositories.playerRecipes().findAll(player.getObjectId()));
		player.setSkillSkinList(GameRepositories.playerSkillSkins().findAll(playerObjId));

		/**
		 * Account warehouse should be already loaded in account
		 */
		Storage accWarehouse = account.getAccountWarehouse();
		player.setStorage(accWarehouse, StorageType.ACCOUNT_WAREHOUSE);
		Storage inventory = DAOManager.getDAO(InventoryDAO.class).loadStorage(playerObjId, StorageType.CUBE);
		ItemService.loadItemStones(inventory.getItems());
		player.setStorage(inventory, StorageType.CUBE);

		for (int petBagId = StorageType.PET_BAG_MIN; petBagId <= StorageType.PET_BAG_MAX; petBagId++) {
			Storage petBag = DAOManager.getDAO(InventoryDAO.class).loadStorage(playerObjId,
					StorageType.getStorageTypeById(petBagId));
			ItemService.loadItemStones(petBag.getItems());
			player.setStorage(petBag, StorageType.getStorageTypeById(petBagId));
		}

		for (int houseWhId = StorageType.HOUSE_WH_MIN; houseWhId <= StorageType.HOUSE_WH_MAX; houseWhId++) {
			StorageType whType = StorageType.getStorageTypeById(houseWhId);
			if (whType != null) {
				Storage cabinet = DAOManager.getDAO(InventoryDAO.class).loadStorage(playerObjId,
						StorageType.getStorageTypeById(houseWhId));
				ItemService.loadItemStones(cabinet.getItems());
				player.setStorage(cabinet, StorageType.getStorageTypeById(houseWhId));
			}
		}

		Storage warehouse = DAOManager.getDAO(InventoryDAO.class).loadStorage(playerObjId,
				StorageType.REGULAR_WAREHOUSE);
		ItemService.loadItemStones(warehouse.getItems());

		player.setStorage(warehouse, StorageType.REGULAR_WAREHOUSE);

		HouseRegistry houseRegistry = null;
		for (House house : player.getHouses()) {
			if (house.getStatus() == HouseStatus.ACTIVE || house.getStatus() == HouseStatus.SELL_WAIT) {
				houseRegistry = house.getRegistry();
				break;
			}
		}
		player.setHouseRegistry(houseRegistry);
		player.getEquipment().onLoadApplyEquipmentStats();
		GameRepositories.playerPunishments().load(player, PunishmentType.PRISON);
		GameRepositories.playerPunishments().load(player, PunishmentType.GATHER);
		player.getController().updatePassiveStats();
		GameRepositories.playerEffects().load(player);
		GameRepositories.skillCooldowns().load(player);
		GameRepositories.itemCooldowns().load(player);
		GameRepositories.portalCooldowns().load(player);
		GameRepositories.houseObjectCooldowns().load(player);
		GameRepositories.playerBindPoints().load(player);
		GameRepositories.craftCooldowns().load(player);
		GameRepositories.lunaShop().load(player);
		if (player.getCommonData().getBonusTitleId() > 0) {
			TitleChangeListener.onBonusTitleChange(player.getGameStats(), player.getCommonData().getTitleId(), true);
		}
		GameRepositories.playerLifeStats().load(player);
		GameRepositories.playerEmotions().load(player);
		if (CacheConfig.CACHE_PLAYERS) {
			playerCache.put(playerObjId, player);
		}
		return player;
	}

	public static Player newPlayer(PlayerCommonData playerCommonData, PlayerAppearance playerAppearance,
			Account account) {
		PlayerInitialData playerInitialData = DataManager.PLAYER_INITIAL_DATA;
		LocationData ld = playerInitialData.getSpawnLocation(playerCommonData.getRace());
		WorldPosition position = World.getInstance().createPosition(ld.getMapId(), ld.getX(), ld.getY(), ld.getZ(),
				ld.getHeading(), 0);
		playerCommonData.setPosition(position);
		Player newPlayer = new Player(new PlayerController(), playerCommonData, playerAppearance, account);
		newPlayer.setSkillList(new PlayerSkillList());
		SkillLearnService.addNewSkills(newPlayer);
		PlayerCreationData playerCreationData = playerInitialData
				.getPlayerCreationData(playerCommonData.getPlayerClass());
		Storage playerInventory = new PlayerStorage(StorageType.CUBE);
		Storage regularWarehouse = new PlayerStorage(StorageType.REGULAR_WAREHOUSE);
		Storage accountWarehouse = new PlayerStorage(StorageType.ACCOUNT_WAREHOUSE);
		Equipment equipment = new Equipment(newPlayer);
		if (playerCreationData != null) {
			List<ItemType> items = playerCreationData.getItems();
			for (ItemType itemType : items) {
				int itemId = itemType.getTemplate().getTemplateId();
				Item item = ItemFactory.newItem(itemId, itemType.getCount());
				if (item == null) {
					continue;
				}
				ItemTemplate itemTemplate = item.getItemTemplate();
				if ((itemTemplate.isArmor() || itemTemplate.isWeapon())
						&& !(equipment.isSlotEquipped(itemTemplate.getItemSlot()))) {
					item.setEquipped(true);
					ItemSlot itemSlot = ItemSlot.getSlotFor(itemTemplate.getItemSlot());
					item.setEquipmentSlot(itemSlot.getSlotIdMask());
					equipment.onLoadHandler(item);
				} else {
					playerInventory.onLoadHandler(item);
				}
			}
		}
		newPlayer.setStorage(playerInventory, StorageType.CUBE);
		newPlayer.setStorage(regularWarehouse, StorageType.REGULAR_WAREHOUSE);
		newPlayer.setStorage(accountWarehouse, StorageType.ACCOUNT_WAREHOUSE);
		newPlayer.setEquipment(equipment);
		newPlayer.setMailbox(new Mailbox(newPlayer));
		for (int petBagId = StorageType.PET_BAG_MIN; petBagId <= StorageType.PET_BAG_MAX; petBagId++) {
			Storage petBag = new PlayerStorage(StorageType.getStorageTypeById(petBagId));
			newPlayer.setStorage(petBag, StorageType.getStorageTypeById(petBagId));
		}
		for (int houseWhId = StorageType.HOUSE_WH_MIN; houseWhId <= StorageType.HOUSE_WH_MAX; houseWhId++) {
			StorageType whType = StorageType.getStorageTypeById(houseWhId);
			if (whType != null) {
				Storage cabinet = new PlayerStorage(whType);
				newPlayer.setStorage(cabinet, StorageType.getStorageTypeById(houseWhId));
			}
		}

		playerInventory.setPersistentState(PersistentState.UPDATE_REQUIRED);
		equipment.setPersistentState(PersistentState.UPDATE_REQUIRED);
		return newPlayer;
	}

	public static boolean cancelPlayerDeletion(PlayerAccountData accData) {
		if (accData.getDeletionDate() == null) {
			return true;
		}
		if (accData.getDeletionDate().getTime() > System.currentTimeMillis()) {
			accData.setDeletionDate(null);
			storeDeletionTime(accData);
			return true;
		}
		return false;
	}

	public static void deletePlayer(PlayerAccountData accData) {
		if (accData.getDeletionDate() != null) {
			return;
		}
		accData.setDeletionDate(new Timestamp(System.currentTimeMillis() + 5 * 60 * 1000));
		storeDeletionTime(accData);
	}

	public static void deletePlayerFromDB(int playerId) {
		DAOManager.getDAO(InventoryDAO.class).deletePlayerItems(playerId);
		DAOManager.getDAO(PlayerDAO.class).deletePlayer(playerId);
	}

	public static int deleteAccountsCharsFromDB(int accountId) {
		List<Integer> charIds = DAOManager.getDAO(PlayerDAO.class).getPlayerOidsOnAccount(accountId);
		for (int playerId : charIds) {
			deletePlayerFromDB(playerId);
		}
		return charIds.size();
	}

	private static void storeDeletionTime(PlayerAccountData accData) {
		DAOManager.getDAO(PlayerDAO.class).updateDeletionTime(accData.getPlayerCommonData().getPlayerObjId(),
				accData.getDeletionDate());
	}

	public static void storeCreationTime(int objectId, Timestamp creationDate) {
		DAOManager.getDAO(PlayerDAO.class).storeCreationTime(objectId, creationDate);
	}

	public static void addMacro(Player player, int macroOrder, String macroXML) {
		if (player.getMacroList().addMacro(macroOrder, macroXML)) {
			GameRepositories.playerMacros().add(player.getObjectId(), macroOrder, macroXML);
		} else {
			GameRepositories.playerMacros().update(player.getObjectId(), macroOrder, macroXML);
		}
	}

	public static void removeMacro(Player player, int macroOrder) {
		if (player.getMacroList().removeMacro(macroOrder)) {
			GameRepositories.playerMacros().remove(player.getObjectId(), macroOrder);
		}
	}

	public static Player getCachedPlayer(int playerObjectId) {
		return playerCache.get(playerObjectId);
	}

	public static String getPlayerName(Integer objectId) {
		return getPlayerNames(Collections.singleton(objectId)).get(objectId);
	}

	public static Map<Integer, String> getPlayerNames(Collection<Integer> playerObjIds) {
		if (GenericValidator.isBlankOrNull(playerObjIds)) {
			return Collections.emptyMap();
		}
		final Map<Integer, String> result = Maps.newHashMap();
		final Set<Integer> playerObjIdsCopy = Sets.newHashSet(playerObjIds);
		World.getInstance().doOnAllPlayers(new Visitor<Player>() {
			@Override
			public void visit(Player object) {
				if (playerObjIdsCopy.contains(object.getObjectId())) {
					result.put(object.getObjectId(), object.getName());
					playerObjIdsCopy.remove(object.getObjectId());
				}
			}
		});
		result.putAll(DAOManager.getDAO(PlayerDAO.class).getPlayerNames(playerObjIdsCopy));
		return result;
	}
}