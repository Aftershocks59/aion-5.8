package com.aionemu.gameserver.network.aion.clientpackets;

import com.aionemu.gameserver.model.DescriptionId;
import com.aionemu.gameserver.model.actions.PlayerMode;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.trade.TradePSItem;
import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionConnection.State;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.services.PrivateStoreService;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author Simple
 */
public class CM_PRIVATE_STORE extends AionClientPacket {

	/**
	 * Private store information
	 */
	private Player activePlayer;
	private TradePSItem[] tradePSItems;
	/** Counts the wire bytes one store entry occupies: objId, itemId, count, price, unknown. */
	private static final int BYTES_PER_ITEM = 4 + 4 + 2 + 4 + 4;

	private int itemCount;
	private boolean cancelStore;

	public CM_PRIVATE_STORE(int opcode, State state, State... restStates) {
		super(opcode, state, restStates);
	}

	@Override
	protected void readImpl() {
		/**
		 * Define who wants to create a private store
		 */
		activePlayer = getConnection().getActivePlayer();

		// Check before dereferencing. The null test used to sit one line below
		// getLevel(), so it guarded nothing: a packet arriving between login and
		// world entry threw rather than returning.
		if (activePlayer == null) {
			return;
		}

		int level = activePlayer.getLevel();
		if (activePlayer.isInPrison()) {
			cancelStore = true;
			PacketSendUtility.sendMessage(activePlayer, "You can't open Private Shop in prison!");
			return;
		}
		if (level < 10) {
			// Characters under level 10 who are using a free trial cannot open a private
			// store
			PacketSendUtility.sendPacket(activePlayer,
					SM_SYSTEM_MESSAGE.STR_FREE_EXPERIENCE_CHARACTER_CANT_OPEN_PERSONAL_SHOP("10"));
			return;
		}
		if (activePlayer.getController().isInCombat()) {
			// You cannot open a private store while fighting
			PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_PERSONAL_SHOP_DISABLED_IN_EXCHANGE);
			// As you cannot open a private store while fighting, it will be closed
			// automatically
			PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_PERSONAL_SHOP_CLOSED_FOR_COMBAT_MODE);
			PrivateStoreService.closePrivateStore(activePlayer);
			return;
		}
		if ((activePlayer.isFlying()) || (activePlayer.isUsingFlyTeleport())
				|| (activePlayer.isInPlayerMode(PlayerMode.WINDSTREAM))) {
			// You cannot open a private store while flying
			PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_PERSONAL_SHOP_DISABLED_IN_FLY_MODE);
			return;
		}
		if (activePlayer.isInPlayerMode(PlayerMode.RIDE)) {
			// You cannot open a private store while mounted
			PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_MSG_PERSONAL_SHOP_RESTRICTION_RIDE);
			return;
		}
		if (activePlayer.getEffectController().isAbnormalSet(AbnormalState.HIDE)) {
			// You cannot open a private store while hiding
			PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_PERSONAL_SHOP_DISABLED_IN_HIDDEN_MODE);
			// Your private store closed automatically because you are currently hiding
			PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_PERSONAL_SHOP_CLOSED_FOR_HIDDEN_MODE);
			PrivateStoreService.closePrivateStore(activePlayer);
			return;
		}
		itemCount = readH();

		// Size the array on what the packet can actually describe, not on the count
		// the client claims. Each entry costs eighteen bytes on the wire, so a small
		// packet declaring 65535 items cannot be honest, and sizing on its word
		// would allocate half a megabyte per packet on demand.
		int describable = getRemainingBytes() / BYTES_PER_ITEM;
		if (itemCount > describable) {
			itemCount = describable;
		}

		tradePSItems = new TradePSItem[itemCount];

		if (activePlayer.getMoveController().isInMove()) {
			// You cannot open the private store on a moving object
			PacketSendUtility.sendPacket(activePlayer, SM_SYSTEM_MESSAGE.STR_PERSONAL_SHOP_DISABLED_IN_MOVING_OBJECT);
			cancelStore = true;
			return;
		}

		for (int i = 0; i < itemCount; i++) {
			int itemObjId = readD();
			int itemId = readD();
			int count = readH();
			long price = readD();
			readD();// unk 4.7
			Item item = activePlayer.getInventory().getItemByObjId(itemObjId);

			// Reject an unknown item before anything dereferences it. The guard used
			// to be folded into a condition ending in "&& !cancelStore", so once a
			// first bad entry had set that flag, a second entry naming an item the
			// player does not own fell through to the else branch and dereferenced
			// null. Two crafted entries were enough to kill the packet thread.
			if (item == null) {
				PacketSendUtility.sendMessage(activePlayer, "Invalid item.");
				cancelStore = true;
				continue;
			}

			if ((price < 0 || item.getItemId() != itemId || item.getItemCount() < count) && !cancelStore) {
				PacketSendUtility.sendMessage(activePlayer, "Invalid item.");
				cancelStore = true;
			} else if (!item.isTradeable(activePlayer)) {
				PacketSendUtility.sendPacket(activePlayer,
						new SM_SYSTEM_MESSAGE(1300344, new DescriptionId(item.getNameId())));
				cancelStore = true;
			}

			tradePSItems[i] = new TradePSItem(itemObjId, itemId, count, price);
		}
	}

	@Override
	protected void runImpl() {
		if (activePlayer == null) {
			return;
		}
		if (activePlayer.getLifeStats().isAlreadyDead()) {
			return;
		}
		if (!cancelStore && itemCount > 0) {
			PrivateStoreService.addItems(activePlayer, tradePSItems);
		} else {
			PrivateStoreService.closePrivateStore(activePlayer);
		}
	}
}
