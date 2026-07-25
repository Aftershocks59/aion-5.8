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
package com.aionemu.gameserver.network.aion.clientpackets;

import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionConnection.State;
import com.aionemu.gameserver.services.LegionService;

/**
 * @author Simple
 */
public class CM_LEGION_UPLOAD_EMBLEM extends AionClientPacket {

	/** Emblem related information **/
	private int size;
	private byte[] data;

	/**
	 * @param opcode
	 */
	public CM_LEGION_UPLOAD_EMBLEM(int opcode, State state, State... restStates) {
		super(opcode, state, restStates);
	}

	@Override
	protected void readImpl() {
		int declared = readD();

		// Refuse a chunk larger than the packet carrying it. The client declares this
		// length, so an unchecked new byte[declared] hands any logged-in player a way
		// to exhaust the heap. readB guards this too; stating it here keeps the
		// intent visible at the call site and drops the chunk instead of truncating.
		if (declared < 0 || declared > getRemainingBytes()) {
			size = 0;
			data = null;
			return;
		}

		size = declared;
		data = readB(declared);
	}

	@Override
	protected void runImpl() {
		if (data != null && data.length > 0) {
			LegionService.getInstance().uploadEmblemData(getConnection().getActivePlayer(), size, data);
		}
	}
}