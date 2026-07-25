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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.RewardType;
import com.aionemu.gameserver.model.templates.atreian_bestiary.AtreianBestiaryTemplate;
import com.aionemu.gameserver.model.templates.atreian_bestiary.AtreianBestiaryTemplate.AtreianBestiaryAchievementTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ATREIAN_BESTIARY;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ATREIAN_BESTIARY_LIST;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author Ranastic
 */

public class AtreianBestiaryService {
	private Logger log = LoggerFactory.getLogger(AtreianBestiaryService.class);

	public void onLogin(Player player) {
		PacketSendUtility.sendPacket(player, new SM_ATREIAN_BESTIARY_LIST(player));
	}

	public void onKill(Player player, int npcId) {
		AtreianBestiaryTemplate template = DataManager.ATREIAN_BESTIARY.getAtreianBestiaryTemplateByNpcId(npcId);
		if (template == null || template.getNpcIds() == null) {
			return;
		}
		for (int tmpNpcId : template.getNpcIds()) {
			if (npcId == tmpNpcId) {
				int killCount = GameRepositories.atreianBestiary().findKillCount(player.getObjectId(),
						template.getId());
				byte currentLvl = (byte) GameRepositories.atreianBestiary().findLevel(player.getObjectId(),
						template.getId());
				killCount++;
				int claimReward = 0;
				for (AtreianBestiaryAchievementTemplate at : template.getAtreianBestiaryAchievementTemplate()) {
					if (at == null) {
						return;
					}
					if (killCount == at.getKillCondition()) {
						claimReward = (currentLvl + 1);
					}
				}
				PacketSendUtility.sendPacket(player,
						new SM_ATREIAN_BESTIARY(template.getId(), killCount, currentLvl, claimReward));
				player.getAtreianBestiary().add(player, template.getId(), killCount, currentLvl, claimReward);
			}
		}
	}

	public void onLvlUp(Player player, int id) {
		AtreianBestiaryTemplate template = DataManager.ATREIAN_BESTIARY.getAtreianBestiaryTemplate(id);
		if (template == null) {
			return;
		}
		int killCount = GameRepositories.atreianBestiary().findKillCount(player.getObjectId(), id);
		byte currentLvl = (byte) GameRepositories.atreianBestiary().findLevel(player.getObjectId(), id);
		int isClaimReward = GameRepositories.atreianBestiary().findClaimedReward(player.getObjectId(), id);
		currentLvl = (byte) (currentLvl + 1);
		long exp = 0;
		for (AtreianBestiaryAchievementTemplate at : template.getAtreianBestiaryAchievementTemplate()) {
			if (killCount == at.getKillCondition()) {
				exp = at.getRewardExp();
			}
		}
		player.getCommonData().addExp(exp, RewardType.MONSTER_BOOK);
		PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_GET_EXP2(exp));
		PacketSendUtility.sendPacket(player, new SM_ATREIAN_BESTIARY(id, killCount, currentLvl, isClaimReward));
		player.getAtreianBestiary().add(player, id, killCount, currentLvl, isClaimReward);
	}

	public static AtreianBestiaryService getInstance() {
		return NewSingletonHolder.INSTANCE;
	}

	private static class NewSingletonHolder {
		private static final AtreianBestiaryService INSTANCE = new AtreianBestiaryService();
	}
}