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

package instance.illuminaryObelisk;

import com.aionemu.commons.network.util.ThreadPoolManager;
import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai2.AIState;
import com.aionemu.gameserver.ai2.AbstractAI;
import com.aionemu.gameserver.controllers.effect.PlayerEffectController;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.instance.handlers.InstanceID;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.drop.DropItem;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.StaticDoor;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.Storage;
import com.aionemu.gameserver.network.aion.serverpackets.*;
import com.aionemu.gameserver.services.drop.DropRegistrationService;
import com.aionemu.gameserver.services.player.PlayerReviveService;
import com.aionemu.gameserver.services.teleport.TeleportService2;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.knownlist.Visitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Supplies what this variant does differently from AbstractIlluminaryObeliskInstance.
 *
 * @author Oraion
 */
@InstanceID(301230000)
public class IlluminaryObeliskInstance extends AbstractIlluminaryObeliskInstance {

	protected void startIlluminaryTimer() {
		//The weakened protective shield will disappear in 30 minutes.
		this.sendMessage(1402129, 1 * 60 * 1000);
		//The weakened protective shield will disappear in 25 minutes.
		this.sendMessage(1402130, 5 * 60 * 1000);
		//The weakened protective shield will disappear in 20 minutes.
		this.sendMessage(1402131, 10 * 60 * 1000);
		//The weakened protective shield will disappear in 15 minutes.
		this.sendMessage(1402132, 15 * 60 * 1000);
		//The weakened protective shield will disappear in 10 minutes.
		this.sendMessage(1402133, 20 * 60 * 1000);
		//The weakened protective shield will disappear in 5 minutes.
		this.sendMessage(1402134, 25 * 60 * 1000);
		//The weakened protective shield will disappear in 1 minute.
		this.sendMessage(1402235, 29 * 60 * 1000);
		//The protective shield covering the Illuminary Obelisk has disappeared. The Pashid Destruction Unit's intense bombing commences.
		this.sendMessage(1402236, 30 * 60 * 1000);
		//The Dynatoum has destroyed the teleport device of the shield generation hub.
		this.sendMessage(1402212, 31 * 60 * 1000);
    }

	@Override
    public void onDie(Npc npc) {
        Player player = npc.getAggroList().getMostPlayerDamage();
		switch (npc.getObjectTemplate().getTemplateId()) {
			case 702010: //Eastern Shield Generator.
		        despawnNpc(npc);
				stopInstanceTask1();
				deleteNpc(702014); //Eastern Pashid Infiltration Gate.
				deleteNpc(702218); //Eastern Defence Charge 01.
				deleteNpc(702219); //Eastern Defence Charge 02.
				deleteNpc(702220); //Eastern Defence Charge 03.
				killNpc(getNpcs(233720)); //Pashid Destruction Unit Combatant.
			    killNpc(getNpcs(233721)); //Pashid Destruction Unit Ambusher.
			    killNpc(getNpcs(233722)); //Pashid Destruction Unit Mage.
				killNpc(getNpcs(233723)); //Pashid Destruction Unit Beastmaster.
				killNpc(getNpcs(233724)); //Pashid Destruction Unit Beastmaster.
				killNpc(getNpcs(233725)); //Pashid Destruction Unit Rearguard.
				killNpc(getNpcs(233726)); //Pashid Destruction Unit Striker.
				killNpc(getNpcs(233727)); //Pashid Destruction Unit Drummer.
				killNpc(getNpcs(233728)); //Pashid Destruction Unit Elite Combatant.
				killNpc(getNpcs(233729)); //Pashid Destruction Unit Elite Ambusher.
				killNpc(getNpcs(233730)); //Pashid Destruction Unit Elite Mage.
				killNpc(getNpcs(233731)); //Pashid Destruction Unit Elite Beastmaster.
				killNpc(getNpcs(233732)); //Pashid Destruction Unit Elite Healer.
				killNpc(getNpcs(233733)); //Pashid Destruction Unit Elite Rearguard.
				killNpc(getNpcs(233734)); //Pashid Destruction Unit Elite Striker.
				//The eastern shield power generator has been destroyed.
				sendMsgByRace(1402139, Race.PC_ALL, 0);
			    ThreadPoolManager.getInstance().schedule(new Runnable() {
				    @Override
				    public void run() {
						spawn(702010, 255.47392f, 293.56177f, 321.18497f, (byte) 89); //Eastern Shield Generator.
				    }
			    }, 10000);
			break;
		    case 702011: //Western Shield Generator.
		        despawnNpc(npc);
				stopInstanceTask2();
				deleteNpc(702015); //Western Pashid Infiltration Gate.
				deleteNpc(702221); //Western Defence Charge 01.
				deleteNpc(702222); //Western Defence Charge 02.
				deleteNpc(702223); //Western Defence Charge 03.
				killNpc(getNpcs(233720)); //Pashid Destruction Unit Combatant.
			    killNpc(getNpcs(233721)); //Pashid Destruction Unit Ambusher.
			    killNpc(getNpcs(233722)); //Pashid Destruction Unit Mage.
				killNpc(getNpcs(233723)); //Pashid Destruction Unit Beastmaster.
				killNpc(getNpcs(233724)); //Pashid Destruction Unit Beastmaster.
				killNpc(getNpcs(233725)); //Pashid Destruction Unit Rearguard.
				killNpc(getNpcs(233726)); //Pashid Destruction Unit Striker.
				killNpc(getNpcs(233727)); //Pashid Destruction Unit Drummer.
				killNpc(getNpcs(233728)); //Pashid Destruction Unit Elite Combatant.
				killNpc(getNpcs(233729)); //Pashid Destruction Unit Elite Ambusher.
				killNpc(getNpcs(233730)); //Pashid Destruction Unit Elite Mage.
				killNpc(getNpcs(233731)); //Pashid Destruction Unit Elite Beastmaster.
				killNpc(getNpcs(233732)); //Pashid Destruction Unit Elite Healer.
				killNpc(getNpcs(233733)); //Pashid Destruction Unit Elite Rearguard.
				killNpc(getNpcs(233734)); //Pashid Destruction Unit Elite Striker.
				//The western shield power generator has been destroyed.
				sendMsgByRace(1402140, Race.PC_ALL, 0);
			    ThreadPoolManager.getInstance().schedule(new Runnable() {
				    @Override
				    public void run() {
						spawn(702011, 255.55742f, 216.03549f, 321.21344f, (byte) 30); //Western Shield Generator.
				    }
			    }, 10000);
			break;
		    case 702012: //Southern Shield Generator.
		        despawnNpc(npc);
				stopInstanceTask3();
				deleteNpc(702016); //Southern Pashid Infiltration Gate.
				deleteNpc(702224); //Southern Defence Charge 01.
				deleteNpc(702225); //Southern Defence Charge 02.
				deleteNpc(702226); //Southern Defence Charge 03.
				killNpc(getNpcs(233720)); //Pashid Destruction Unit Combatant.
			    killNpc(getNpcs(233721)); //Pashid Destruction Unit Ambusher.
			    killNpc(getNpcs(233722)); //Pashid Destruction Unit Mage.
				killNpc(getNpcs(233723)); //Pashid Destruction Unit Beastmaster.
				killNpc(getNpcs(233724)); //Pashid Destruction Unit Beastmaster.
				killNpc(getNpcs(233725)); //Pashid Destruction Unit Rearguard.
				killNpc(getNpcs(233726)); //Pashid Destruction Unit Striker.
				killNpc(getNpcs(233727)); //Pashid Destruction Unit Drummer.
				killNpc(getNpcs(233728)); //Pashid Destruction Unit Elite Combatant.
				killNpc(getNpcs(233729)); //Pashid Destruction Unit Elite Ambusher.
				killNpc(getNpcs(233730)); //Pashid Destruction Unit Elite Mage.
				killNpc(getNpcs(233731)); //Pashid Destruction Unit Elite Beastmaster.
				killNpc(getNpcs(233732)); //Pashid Destruction Unit Elite Healer.
				killNpc(getNpcs(233733)); //Pashid Destruction Unit Elite Rearguard.
				killNpc(getNpcs(233734)); //Pashid Destruction Unit Elite Striker.
				//The southern shield power generator has been destroyed.
				sendMsgByRace(1402141, Race.PC_ALL, 0);
			    ThreadPoolManager.getInstance().schedule(new Runnable() {
				    @Override
				    public void run() {
						spawn(702012, 294.20718f, 254.60352f, 295.7729f, (byte) 60); //Southern Shield Generator.
				    }
			    }, 10000);
			break;
		    case 702013: //Northern Shield Generator.
		        despawnNpc(npc);
				stopInstanceTask4();
				deleteNpc(702017); //Northern Pashid Infiltration Gate.
				deleteNpc(702227); //Northern Defence Charge 01.
				deleteNpc(702228); //Northern Defence Charge 02.
				deleteNpc(702229); //Northern Defence Charge 03.
				killNpc(getNpcs(233720)); //Pashid Destruction Unit Combatant.
			    killNpc(getNpcs(233721)); //Pashid Destruction Unit Ambusher.
			    killNpc(getNpcs(233722)); //Pashid Destruction Unit Mage.
				killNpc(getNpcs(233723)); //Pashid Destruction Unit Beastmaster.
				killNpc(getNpcs(233724)); //Pashid Destruction Unit Beastmaster.
				killNpc(getNpcs(233725)); //Pashid Destruction Unit Rearguard.
				killNpc(getNpcs(233726)); //Pashid Destruction Unit Striker.
				killNpc(getNpcs(233727)); //Pashid Destruction Unit Drummer.
				killNpc(getNpcs(233728)); //Pashid Destruction Unit Elite Combatant.
				killNpc(getNpcs(233729)); //Pashid Destruction Unit Elite Ambusher.
				killNpc(getNpcs(233730)); //Pashid Destruction Unit Elite Mage.
				killNpc(getNpcs(233731)); //Pashid Destruction Unit Elite Beastmaster.
				killNpc(getNpcs(233732)); //Pashid Destruction Unit Elite Healer.
				killNpc(getNpcs(233733)); //Pashid Destruction Unit Elite Rearguard.
				killNpc(getNpcs(233734)); //Pashid Destruction Unit Elite Striker.
				//The northern shield power generator has been destroyed.
				sendMsgByRace(1402142, Race.PC_ALL, 0);
			    ThreadPoolManager.getInstance().schedule(new Runnable() {
				    @Override
				    public void run() {
						spawn(702013, 216.97739f, 254.4616f, 295.77353f, (byte) 0); //Northern Shield Generator.
				    }
			    }, 10000);
			break;
			case 233720: //Pashid Destruction Unit Combatant.
			case 233721: //Pashid Destruction Unit Ambusher.
			case 233722: //Pashid Destruction Unit Mage.
			case 233723: //Pashid Destruction Unit Beastmaster.
			case 233724: //Pashid Destruction Unit Healer.
			case 233725: //Pashid Destruction Unit Rearguard.
			case 233726: //Pashid Destruction Unit Striker.
			case 233727: //Pashid Destruction Unit Drummer.
			case 233728: //Pashid Destruction Unit Elite Combatant.
			case 233729: //Pashid Destruction Unit Elite Ambusher.
			case 233730: //Pashid Destruction Unit Elite Mage.
			case 233731: //Pashid Destruction Unit Elite Beastmaster.
			case 233732: //Pashid Destruction Unit Elite Healer.
			case 233733: //Pashid Destruction Unit Elite Rearguard.
			case 233734: //Pashid Destruction Unit Elite Striker.
				despawnNpc(npc);
			break;
			case 233740: //Test Weapon Dynatoum.
				despawnNpc(npc);
				sendMsg("[SUCCES]: You have finished <Illuminary Obelisk>");
				spawn(702018, 258.84213f, 251.32626f, 455.12192f, (byte) 105); //Supply Box.
				spawn(730905, 255.36038f, 254.56577f, 455.12015f, (byte) 105); //Illuminary Obelisk Exit.
				switch (Rnd.get(1, 2)) {
		            case 1:
				        spawn(702658, 252.05019f, 257.85583f, 455.12195f, (byte) 105); //Abbey Box.
					break;
					case 2:
					    spawn(702659, 252.05019f, 257.85583f, 455.12195f, (byte) 105); //Noble Abbey Box.
					break;
				}
			break;
		}
    }

	protected void startEasternShield1() {
		easternTaskE1 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233720, 252.24573f, 333.1747f, 325.59268f, (byte) 90));
				rushIlluminary((Npc)spawn(233721, 254.23112f, 333.21808f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233722, 258.46628f, 333.40833f, 325.51834f, (byte) 90));
				rushIlluminary((Npc)spawn(233723, 256.2306f, 333.3805f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233724, 259.83197f, 333.34024f, 325.64847f, (byte) 90));
			}
		}, 1000);
		easternTaskE1 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233725, 252.24573f, 333.1747f, 325.59268f, (byte) 90));
				rushIlluminary((Npc)spawn(233726, 254.23112f, 333.21808f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233727, 258.46628f, 333.40833f, 325.51834f, (byte) 90));
				rushIlluminary((Npc)spawn(233728, 256.2306f, 333.3805f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233729, 259.83197f, 333.34024f, 325.64847f, (byte) 90));
			}
		}, 30000);
	}

	protected void startEasternShield2() {
		easternTaskE2 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233730, 252.24573f, 333.1747f, 325.59268f, (byte) 90));
				rushIlluminary((Npc)spawn(233731, 254.23112f, 333.21808f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233732, 258.46628f, 333.40833f, 325.51834f, (byte) 90));
				rushIlluminary((Npc)spawn(233733, 256.2306f, 333.3805f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233734, 259.83197f, 333.34024f, 325.64847f, (byte) 90));
			}
		}, 1000);
		easternTaskE2 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233720, 252.24573f, 333.1747f, 325.59268f, (byte) 90));
				rushIlluminary((Npc)spawn(233721, 254.23112f, 333.21808f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233722, 258.46628f, 333.40833f, 325.51834f, (byte) 90));
				rushIlluminary((Npc)spawn(233723, 256.2306f, 333.3805f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233724, 259.83197f, 333.34024f, 325.64847f, (byte) 90));
			}
		}, 30000);
	}

	protected void startEasternShield3() {
		easternTaskE3 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233725, 252.24573f, 333.1747f, 325.59268f, (byte) 90));
				rushIlluminary((Npc)spawn(233726, 254.23112f, 333.21808f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233727, 258.46628f, 333.40833f, 325.51834f, (byte) 90));
				rushIlluminary((Npc)spawn(233728, 256.2306f, 333.3805f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233729, 259.83197f, 333.34024f, 325.64847f, (byte) 90));
			}
		}, 1000);
		easternTaskE3 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233730, 252.24573f, 333.1747f, 325.59268f, (byte) 90));
				rushIlluminary((Npc)spawn(233731, 254.23112f, 333.21808f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233732, 258.46628f, 333.40833f, 325.51834f, (byte) 90));
				rushIlluminary((Npc)spawn(233733, 256.2306f, 333.3805f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233734, 259.83197f, 333.34024f, 325.64847f, (byte) 90));
			}
		}, 30000);
	}

	protected void startEasternShield4() {
		easternTaskE4 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233720, 252.24573f, 333.1747f, 325.59268f, (byte) 90));
				rushIlluminary((Npc)spawn(233721, 254.23112f, 333.21808f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233722, 258.46628f, 333.40833f, 325.51834f, (byte) 90));
				rushIlluminary((Npc)spawn(233723, 256.2306f, 333.3805f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233724, 259.83197f, 333.34024f, 325.64847f, (byte) 90));
			}
		}, 1000);
		easternTaskE4 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233725, 252.24573f, 333.1747f, 325.59268f, (byte) 90));
				rushIlluminary((Npc)spawn(233726, 254.23112f, 333.21808f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233727, 258.46628f, 333.40833f, 325.51834f, (byte) 90));
				rushIlluminary((Npc)spawn(233728, 256.2306f, 333.3805f, 325.49332f, (byte) 90));
				rushIlluminary((Npc)spawn(233729, 259.83197f, 333.34024f, 325.64847f, (byte) 90));
			}
		}, 30000);
	}

	protected void startWesternShield1() {
		westernTaskW1 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233720, 258.78595f, 176.05591f, 325.59268f, (byte) 30));
				rushIlluminary((Npc)spawn(233721, 257.29633f, 176.01747f, 325.55893f, (byte) 30));
				rushIlluminary((Npc)spawn(233722, 253.48524f, 175.99721f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233723, 255.67467f, 176.00883f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233724, 251.44252f, 175.98637f, 325.64847f, (byte) 30));
			}
		}, 1000);
		westernTaskW1 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233725, 258.78595f, 176.05591f, 325.59268f, (byte) 30));
				rushIlluminary((Npc)spawn(233726, 257.29633f, 176.01747f, 325.55893f, (byte) 30));
				rushIlluminary((Npc)spawn(233727, 253.48524f, 175.99721f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233728, 255.67467f, 176.00883f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233729, 251.44252f, 175.98637f, 325.64847f, (byte) 30));
			}
		}, 30000);
	}

	protected void startWesternShield2() {
		westernTaskW2 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233730, 258.78595f, 176.05591f, 325.59268f, (byte) 30));
				rushIlluminary((Npc)spawn(233731, 257.29633f, 176.01747f, 325.55893f, (byte) 30));
				rushIlluminary((Npc)spawn(233732, 253.48524f, 175.99721f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233733, 255.67467f, 176.00883f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233734, 251.44252f, 175.98637f, 325.64847f, (byte) 30));
			}
		}, 1000);
		westernTaskW2 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233720, 258.78595f, 176.05591f, 325.59268f, (byte) 30));
				rushIlluminary((Npc)spawn(233721, 257.29633f, 176.01747f, 325.55893f, (byte) 30));
				rushIlluminary((Npc)spawn(233722, 253.48524f, 175.99721f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233723, 255.67467f, 176.00883f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233724, 251.44252f, 175.98637f, 325.64847f, (byte) 30));
			}
		}, 30000);
	}

	protected void startWesternShield3() {
		westernTaskW3 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233725, 258.78595f, 176.05591f, 325.59268f, (byte) 30));
				rushIlluminary((Npc)spawn(233726, 257.29633f, 176.01747f, 325.55893f, (byte) 30));
				rushIlluminary((Npc)spawn(233727, 253.48524f, 175.99721f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233728, 255.67467f, 176.00883f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233729, 251.44252f, 175.98637f, 325.64847f, (byte) 30));
			}
		}, 1000);
		westernTaskW3 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233730, 258.78595f, 176.05591f, 325.59268f, (byte) 30));
				rushIlluminary((Npc)spawn(233731, 257.29633f, 176.01747f, 325.55893f, (byte) 30));
				rushIlluminary((Npc)spawn(233732, 253.48524f, 175.99721f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233733, 255.67467f, 176.00883f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233734, 251.44252f, 175.98637f, 325.64847f, (byte) 30));
			}
		}, 30000);
	}

	protected void startWesternShield4() {
		westernTaskW4 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233720, 258.78595f, 176.05591f, 325.59268f, (byte) 30));
				rushIlluminary((Npc)spawn(233721, 257.29633f, 176.01747f, 325.55893f, (byte) 30));
				rushIlluminary((Npc)spawn(233722, 253.48524f, 175.99721f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233723, 255.67467f, 176.00883f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233724, 251.44252f, 175.98637f, 325.64847f, (byte) 30));
			}
		}, 1000);
		westernTaskW4 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233725, 258.78595f, 176.05591f, 325.59268f, (byte) 30));
				rushIlluminary((Npc)spawn(233726, 257.29633f, 176.01747f, 325.55893f, (byte) 30));
				rushIlluminary((Npc)spawn(233727, 253.48524f, 175.99721f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233728, 255.67467f, 176.00883f, 325.49332f, (byte) 30));
				rushIlluminary((Npc)spawn(233729, 251.44252f, 175.98637f, 325.64847f, (byte) 30));
			}
		}, 30000);
	}

	protected void startSouthernShield1() {
		southernTaskS1 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233720, 336.21823f, 258.05798f, 292.4295f, (byte) 60));
				rushIlluminary((Npc)spawn(233721, 336.28296f, 256.22827f, 292.3325f, (byte) 60));
				rushIlluminary((Npc)spawn(233722, 336.35062f, 252.48618f, 292.33862f, (byte) 60));
				rushIlluminary((Npc)spawn(233723, 336.3128f, 254.57924f, 292.33252f, (byte) 60));
				rushIlluminary((Npc)spawn(233724, 336.38608f, 250.51807f, 292.46326f, (byte) 60));
			}
		}, 1000);
		southernTaskS1 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233725, 336.21823f, 258.05798f, 292.4295f, (byte) 60));
				rushIlluminary((Npc)spawn(233726, 336.28296f, 256.22827f, 292.3325f, (byte) 60));
				rushIlluminary((Npc)spawn(233727, 336.35062f, 252.48618f, 292.33862f, (byte) 60));
				rushIlluminary((Npc)spawn(233728, 336.3128f, 254.57924f, 292.33252f, (byte) 60));
				rushIlluminary((Npc)spawn(233729, 336.38608f, 250.51807f, 292.46326f, (byte) 60));
			}
		}, 30000);
	}

	protected void startSouthernShield2() {
		southernTaskS2 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233730, 336.21823f, 258.05798f, 292.4295f, (byte) 60));
				rushIlluminary((Npc)spawn(233731, 336.28296f, 256.22827f, 292.3325f, (byte) 60));
				rushIlluminary((Npc)spawn(233732, 336.35062f, 252.48618f, 292.33862f, (byte) 60));
				rushIlluminary((Npc)spawn(233733, 336.3128f, 254.57924f, 292.33252f, (byte) 60));
				rushIlluminary((Npc)spawn(233734, 336.38608f, 250.51807f, 292.46326f, (byte) 60));
			}
		}, 1000);
		southernTaskS2 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233720, 336.21823f, 258.05798f, 292.4295f, (byte) 60));
				rushIlluminary((Npc)spawn(233721, 336.28296f, 256.22827f, 292.3325f, (byte) 60));
				rushIlluminary((Npc)spawn(233722, 336.35062f, 252.48618f, 292.33862f, (byte) 60));
				rushIlluminary((Npc)spawn(233723, 336.3128f, 254.57924f, 292.33252f, (byte) 60));
				rushIlluminary((Npc)spawn(233724, 336.38608f, 250.51807f, 292.46326f, (byte) 60));
			}
		}, 30000);
	}

	protected void startSouthernShield3() {
		southernTaskS3 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233725, 336.21823f, 258.05798f, 292.4295f, (byte) 60));
				rushIlluminary((Npc)spawn(233726, 336.28296f, 256.22827f, 292.3325f, (byte) 60));
				rushIlluminary((Npc)spawn(233727, 336.35062f, 252.48618f, 292.33862f, (byte) 60));
				rushIlluminary((Npc)spawn(233728, 336.3128f, 254.57924f, 292.33252f, (byte) 60));
				rushIlluminary((Npc)spawn(233729, 336.38608f, 250.51807f, 292.46326f, (byte) 60));
			}
		}, 1000);
		southernTaskS3 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233730, 336.21823f, 258.05798f, 292.4295f, (byte) 60));
				rushIlluminary((Npc)spawn(233731, 336.28296f, 256.22827f, 292.3325f, (byte) 60));
				rushIlluminary((Npc)spawn(233732, 336.35062f, 252.48618f, 292.33862f, (byte) 60));
				rushIlluminary((Npc)spawn(233733, 336.3128f, 254.57924f, 292.33252f, (byte) 60));
				rushIlluminary((Npc)spawn(233734, 336.38608f, 250.51807f, 292.46326f, (byte) 60));
			}
		}, 30000);
	}

	protected void startSouthernShield4() {
		southernTaskS4 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233720, 336.21823f, 258.05798f, 292.4295f, (byte) 60));
				rushIlluminary((Npc)spawn(233721, 336.28296f, 256.22827f, 292.3325f, (byte) 60));
				rushIlluminary((Npc)spawn(233722, 336.35062f, 252.48618f, 292.33862f, (byte) 60));
				rushIlluminary((Npc)spawn(233723, 336.3128f, 254.57924f, 292.33252f, (byte) 60));
				rushIlluminary((Npc)spawn(233724, 336.38608f, 250.51807f, 292.46326f, (byte) 60));
			}
		}, 1000);
		southernTaskS4 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233725, 336.21823f, 258.05798f, 292.4295f, (byte) 60));
				rushIlluminary((Npc)spawn(233726, 336.28296f, 256.22827f, 292.3325f, (byte) 60));
				rushIlluminary((Npc)spawn(233727, 336.35062f, 252.48618f, 292.33862f, (byte) 60));
				rushIlluminary((Npc)spawn(233728, 336.3128f, 254.57924f, 292.33252f, (byte) 60));
				rushIlluminary((Npc)spawn(233729, 336.38608f, 250.51807f, 292.46326f, (byte) 60));
			}
		}, 30000);
	}

	protected void startNorthernShield1() {
		northernTaskN1 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233720, 176.56479f, 251.09068f, 292.42026f, (byte) 119));
				rushIlluminary((Npc)spawn(233721, 176.4995f, 252.93555f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233722, 176.41188f, 257.24088f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233723, 176.4588f, 254.93521f, 292.33252f, (byte) 0));
				rushIlluminary((Npc)spawn(233724, 176.37492f, 259.05646f, 292.55435f, (byte) 0));
			}
		}, 1000);
		northernTaskN1 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233725, 176.56479f, 251.09068f, 292.42026f, (byte) 119));
				rushIlluminary((Npc)spawn(233726, 176.4995f, 252.93555f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233727, 176.41188f, 257.24088f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233728, 176.4588f, 254.93521f, 292.33252f, (byte) 0));
				rushIlluminary((Npc)spawn(233729, 176.37492f, 259.05646f, 292.55435f, (byte) 0));
			}
		}, 30000);
	}

	protected void startNorthernShield2() {
		northernTaskN2 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233730, 176.56479f, 251.09068f, 292.42026f, (byte) 119));
				rushIlluminary((Npc)spawn(233731, 176.4995f, 252.93555f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233732, 176.41188f, 257.24088f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233733, 176.4588f, 254.93521f, 292.33252f, (byte) 0));
				rushIlluminary((Npc)spawn(233734, 176.37492f, 259.05646f, 292.55435f, (byte) 0));
			}
		}, 1000);
		northernTaskN2 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233720, 176.56479f, 251.09068f, 292.42026f, (byte) 119));
				rushIlluminary((Npc)spawn(233721, 176.4995f, 252.93555f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233722, 176.41188f, 257.24088f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233723, 176.4588f, 254.93521f, 292.33252f, (byte) 0));
				rushIlluminary((Npc)spawn(233724, 176.37492f, 259.05646f, 292.55435f, (byte) 0));
			}
		}, 30000);
	}

	protected void startNorthernShield3() {
		northernTaskN3 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233725, 176.56479f, 251.09068f, 292.42026f, (byte) 119));
				rushIlluminary((Npc)spawn(233726, 176.4995f, 252.93555f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233727, 176.41188f, 257.24088f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233728, 176.4588f, 254.93521f, 292.33252f, (byte) 0));
				rushIlluminary((Npc)spawn(233729, 176.37492f, 259.05646f, 292.55435f, (byte) 0));
			}
		}, 1000);
		northernTaskN3 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233730, 176.56479f, 251.09068f, 292.42026f, (byte) 119));
				rushIlluminary((Npc)spawn(233731, 176.4995f, 252.93555f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233732, 176.41188f, 257.24088f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233733, 176.4588f, 254.93521f, 292.33252f, (byte) 0));
				rushIlluminary((Npc)spawn(233734, 176.37492f, 259.05646f, 292.55435f, (byte) 0));
			}
		}, 30000);
	}

	protected void startNorthernShield4() {
		northernTaskN4 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233720, 176.56479f, 251.09068f, 292.42026f, (byte) 119));
				rushIlluminary((Npc)spawn(233721, 176.4995f, 252.93555f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233722, 176.41188f, 257.24088f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233723, 176.4588f, 254.93521f, 292.33252f, (byte) 0));
				rushIlluminary((Npc)spawn(233724, 176.37492f, 259.05646f, 292.55435f, (byte) 0));
			}
		}, 1000);
		northernTaskN4 = ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				rushIlluminary((Npc)spawn(233725, 176.56479f, 251.09068f, 292.42026f, (byte) 119));
				rushIlluminary((Npc)spawn(233726, 176.4995f, 252.93555f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233727, 176.41188f, 257.24088f, 292.3325f, (byte) 0));
				rushIlluminary((Npc)spawn(233728, 176.4588f, 254.93521f, 292.33252f, (byte) 0));
				rushIlluminary((Npc)spawn(233729, 176.37492f, 259.05646f, 292.55435f, (byte) 0));
			}
		}, 30000);
	}

}
