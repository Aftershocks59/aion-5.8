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
 * Holds everything the Illuminary Obelisk instance and its infernal variant run identically.
 *
 * The two handlers shared 34 byte-identical methods. Every one of them
 * lives here; each subclass keeps only what it does differently.
 *
 * @author Oraion
 */
public abstract class AbstractIlluminaryObeliskInstance extends GeneralInstanceHandler {

	protected long startTime;
	protected Race videoRace;
	protected int illuminaryWave;
	protected Future<?> instanceTimer;
	protected Future<?> easternTaskE1;
	protected Future<?> easternTaskE2;
	protected Future<?> easternTaskE3;
	protected Future<?> easternTaskE4;
	protected Future<?> westernTaskW1;
	protected Future<?> westernTaskW2;
	protected Future<?> westernTaskW3;
	protected Future<?> westernTaskW4;
	protected Future<?> southernTaskS1;
	protected Future<?> southernTaskS2;
	protected Future<?> southernTaskS3;
	protected Future<?> southernTaskS4;
	protected Future<?> northernTaskN1;
	protected Future<?> northernTaskN2;
	protected Future<?> northernTaskN3;
	protected Future<?> northernTaskN4;
	protected Map<Integer, StaticDoor> doors;
	protected boolean isInstanceDestroyed = false;
	protected List<Integer> movies = new ArrayList<Integer>();
	protected final List<Future<?>> illuminaryTask1 = new ArrayList<>();
	protected final List<Future<?>> illuminaryTask2 = new ArrayList<>();
	protected final List<Future<?>> illuminaryTask3 = new ArrayList<>();
	protected final List<Future<?>> illuminaryTask4 = new ArrayList<>();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startEasternShield1();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startEasternShield2();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startEasternShield3();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startEasternShield4();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startIlluminaryTimer();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startNorthernShield1();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startNorthernShield2();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startNorthernShield3();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startNorthernShield4();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startSouthernShield1();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startSouthernShield2();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startSouthernShield3();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startSouthernShield4();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startWesternShield1();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startWesternShield2();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startWesternShield3();

	/** Lets each variant answer with its own behaviour. */
	protected abstract void startWesternShield4();

	public void onDropRegistered(Npc npc) {
		Set<DropItem> dropItems = DropRegistrationService.getInstance().getCurrentDropMap().get(npc.getObjectId());
		int npcId = npc.getNpcId();
		int index = dropItems.size() + 1;
		switch (npcId) {
			case 702018: //Supply Box.
				for (Player player: instance.getPlayersInside()) {
				    if (player.isOnline()) {
						dropItems.add(DropRegistrationService.getInstance().regDropItem(index++, player.getObjectId(), npcId, 188053789, 1)); //Major Stigma Support Bundle.
						dropItems.add(DropRegistrationService.getInstance().regDropItem(index++, player.getObjectId(), npcId, 188053083, 1)); //Tempering Solution Chest.
						dropItems.add(DropRegistrationService.getInstance().regDropItem(index++, player.getObjectId(), npcId, 188053100, 1)); //Pure Dynatoum's Equipment Crux Box.
					} switch (Rnd.get(1, 2)) {
				        case 1:
				            dropItems.add(DropRegistrationService.getInstance().regDropItem(index++, player.getObjectId(), npcId, 188052830, 1)); //Dynatoum's Brazen Weapon Box.
				        break;
					    case 2:
				            dropItems.add(DropRegistrationService.getInstance().regDropItem(index++, player.getObjectId(), npcId, 188052831, 1)); //Dynatoum's Brazen Armor Box.
				        break;
					}
				}
			break;
			case 702658: //Abbey Box.
				dropItems.add(DropRegistrationService.getInstance().regDropItem(1, 0, npcId, 188053579, 1)); //[Event] Abbey Bundle.
		    break;
			case 702659: //Noble Abbey Box.
				dropItems.add(DropRegistrationService.getInstance().regDropItem(1, 0, npcId, 188053580, 1)); //[Event] Noble Abbey Bundle.
		    break;
		   /**
			* Each "Shield Generator" unit needs 3 ide items, 12 items in total, you can find them all around the instance.
			*/
			case 730884: //Flourishing Idium.
				dropItems.add(DropRegistrationService.getInstance().regDropItem(1, 0, npcId, 164000289, 3));
			break;
		   /**
			* Bombs to use the cannons appear in chests around the instance in a different place every time, collect them too.
			*/
			case 730885: //Danuar Cannonballs.
				dropItems.add(DropRegistrationService.getInstance().regDropItem(1, 0, npcId, 164000290, 3));
			break;
		}
	}

	@Override
	public void onInstanceCreate(WorldMapInstance instance) {
		super.onInstanceCreate(instance);
		doors = instance.getDoors();
	}

	@Override
	public void onEnterInstance(final Player player) {
		super.onInstanceCreate(instance);
		if (instanceTimer == null) {
			startTime = System.currentTimeMillis();
			instanceTimer = ThreadPoolManager.getInstance().schedule(new Runnable() {
				@Override
				public void run() {
					startIlluminaryTimer();
					doors.get(129).setOpen(true);
				}
			}, 30000); //...30Sec
		}
		final int illuminaryVideo = videoRace == Race.ASMODIANS ? 895 : 894;
		PacketSendUtility.sendPacket(player, new SM_PLAY_MOVIE(0, illuminaryVideo));
	}

	@Override
	public void handleUseItemFinish(Player player, Npc npc) {
		switch (npc.getNpcId()) {
			case 702010: //Eastern Shield Generator.
			    if (player.getInventory().decreaseByItemId(164000289, 3)) {
					startEasternTask();
					startEasternShield1();
					//An Abyss Gate has opened near the eastern power shield generator.
					//Infiltration by Pashid Destruction Unit is underway.
					sendMsgByRace(1402224, Race.PC_ALL, 1000);
					spawn(702014, 255.7926f, 338.22058f, 325.56473f, (byte) 0, 60); //Pashid Infiltration Gate.
				} else {
					//You need a Crystalline Idium Piece to charge the generator.
					PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(1402211));
				}
			break;
			case 702011: //Western Shield Generator.
			    if (player.getInventory().decreaseByItemId(164000289, 3)) {
					startWesternTask();
					startWesternShield1();
					//An Abyss Gate has opened near the western power shield generator.
					//Infiltration by Pashid Destruction Unit is underway.
					sendMsgByRace(1402225, Race.PC_ALL, 1000);
					spawn(702015, 255.7034f, 171.83853f, 325.81653f, (byte) 0, 18); //Pashid Infiltration Gate.
				} else {
					//You need a Crystalline Idium Piece to charge the generator.
					PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(1402211));
				}
			break;
			case 702012: //Southern Shield Generator.
			    if (player.getInventory().decreaseByItemId(164000289, 3)) {
					startSouthernTask();
					startSouthernShield1();
					//An Abyss Gate has opened near the southern power shield generator.
					//Infiltration by Pashid Destruction Unit is underway.
					sendMsgByRace(1402226, Race.PC_ALL, 1000);
					spawn(702016, 343.12021f, 254.10585f, 291.62302f, (byte) 0, 34); //Pashid Infiltration Gate.
				} else {
					//You need a Crystalline Idium Piece to charge the generator.
					PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(1402211));
				}
			break;
			case 702013: //Northern Shield Generator.
			    if (player.getInventory().decreaseByItemId(164000289, 3)) {
					startNorthernTask();
					startNorthernShield1();
					//An Abyss Gate has opened near the northern power shield generator.
					//Infiltration by Pashid Destruction Unit is underway.
					sendMsgByRace(1402227, Race.PC_ALL, 1000);
					spawn(702017, 169.55626f, 254.52907f, 293.04276f, (byte) 0, 17); //Pashid Infiltration Gate.
				} else {
					//You need a Crystalline Idium Piece to charge the generator.
					PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(1402211));
				}
			break;
			case 730886: //Shield Control Room Teleporter.
				instance.doOnAllPlayers(new Visitor<Player>() {
					@Override
					public void visit(Player player) {
						if (player.isOnline()) {
							illuminaryToDynatoum(player);
						}
					}
				});
			break;
		   /**
			* Defense Cannons:
			* Each Shield Unit has a defense cannon that can be used.
			* This cannons do powerful wide area damage attacks.
			* In order to use them you need to have Bomb items.
			* When a shield is charged completely a cannon will spawn to help in the defense of the area.
			* Determining a person to use the cannon and positioning before the mobs come is a recommended.
			* Bombs to use the cannons appear in chests around the instance in a different place every time, collect them too.
			*/
			case 702009: //Danuar Cannon.
			case 702021: //Danuar Cannon.
			case 702022: //Danuar Cannon.
			case 702023: //Danuar Cannon.
			    despawnNpc(npc);
				SkillEngine.getInstance().getSkill(npc, 21511, 60, player).useNoAnimationSkill();
			break;
		}
	}

	//===========================//
	//=== Eastern Shield Task ===//
	//===========================//
	protected void startEasternTask() {
		illuminaryTask1.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				startEasternShield2();
				easternTaskE1.cancel(true);
				//Prepare for combat! More enemies swarming in!
				sendMsgByRace(1402832, Race.PC_ALL, 0);
				//Hold a little longer and you will survive.
				sendMsgByRace(1402833, Race.PC_ALL, 30000);
				spawn(702218, 255.56438f, 297.59488f, 321.39154f, (byte) 29); //Eastern Defence Charge 01.
            }
        }, 120000)); //...2Min
		illuminaryTask1.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				startEasternShield3();
				easternTaskE2.cancel(true);
				//Prepare for combat! More enemies swarming in!
				sendMsgByRace(1402832, Race.PC_ALL, 0);
				//Hold a little longer and you will survive.
				sendMsgByRace(1402833, Race.PC_ALL, 30000);
				spawn(702219, 255.56438f, 297.59488f, 321.39154f, (byte) 29); //Eastern Defence Charge 02.
            }
        }, 240000)); //...4Min
		illuminaryTask1.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				startEasternShield4();
				easternTaskE3.cancel(true);
				//Prepare for combat! More enemies swarming in!
				sendMsgByRace(1402832, Race.PC_ALL, 0);
				//Hold a little longer and you will survive.
				sendMsgByRace(1402833, Race.PC_ALL, 30000);
            }
        }, 360000)); //...6Min
		illuminaryTask1.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				spawn(702220, 255.56438f, 297.59488f, 321.39154f, (byte) 29); //Eastern Defence Charge 03.
				instance.doOnAllPlayers(new Visitor<Player>() {
				    @Override
				    public void visit(Player player) {
						illuminaryWave++;
						stopInstance1(player);
						easternTaskE4.cancel(true);
				    }
			    });
            }
        }, 480000)); //...8Min
	}

	//===========================//
	//=== Western Shield Task ===//
	//===========================//
	protected void startWesternTask() {
		illuminaryTask2.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				startWesternShield2();
				westernTaskW1.cancel(true);
				//Prepare for combat! More enemies swarming in!
				sendMsgByRace(1402832, Race.PC_ALL, 0);
				//Hold a little longer and you will survive.
				sendMsgByRace(1402833, Race.PC_ALL, 30000);
				spawn(702221, 255.38777f, 212.00926f, 321.37292f, (byte) 90); //Western Defence Charge 01.
            }
        }, 120000)); //...2Min
		illuminaryTask2.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				startWesternShield3();
				westernTaskW2.cancel(true);
				//Prepare for combat! More enemies swarming in!
				sendMsgByRace(1402832, Race.PC_ALL, 0);
				//Hold a little longer and you will survive.
				sendMsgByRace(1402833, Race.PC_ALL, 30000);
				spawn(702222, 255.38777f, 212.00926f, 321.37292f, (byte) 90); //Western Defence Charge 02.
            }
        }, 240000)); //...4Min
		illuminaryTask2.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				startWesternShield4();
				westernTaskW3.cancel(true);
				//Prepare for combat! More enemies swarming in!
				sendMsgByRace(1402832, Race.PC_ALL, 0);
				//Hold a little longer and you will survive.
				sendMsgByRace(1402833, Race.PC_ALL, 30000);
            }
        }, 360000)); //...6Min
		illuminaryTask2.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				spawn(702223, 255.38777f, 212.00926f, 321.37292f, (byte) 90); //Western Defence Charge 03.
				instance.doOnAllPlayers(new Visitor<Player>() {
				    @Override
				    public void visit(Player player) {
						illuminaryWave++;
						stopInstance2(player);
						westernTaskW4.cancel(true);
				    }
			    });
            }
        }, 480000)); //...8Min
	}

	//==========================//
	//== Southern Shield Task ==//
	//==========================//
	protected void startSouthernTask() {
		illuminaryTask3.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				startSouthernShield2();
				southernTaskS1.cancel(true);
				//Prepare for combat! More enemies swarming in!
				sendMsgByRace(1402832, Race.PC_ALL, 0);
				//Hold a little longer and you will survive.
				sendMsgByRace(1402833, Race.PC_ALL, 30000);
				spawn(702224, 298.13452f, 254.48087f, 295.93027f, (byte) 119); //Southern Defence Charge 01.
            }
        }, 120000)); //...2Min
		illuminaryTask3.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				startSouthernShield3();
				southernTaskS2.cancel(true);
				//Prepare for combat! More enemies swarming in!
				sendMsgByRace(1402832, Race.PC_ALL, 0);
				//Hold a little longer and you will survive.
				sendMsgByRace(1402833, Race.PC_ALL, 30000);
				spawn(702225, 298.13452f, 254.48087f, 295.93027f, (byte) 119); //Southern Defence Charge 02.
            }
        }, 240000)); //...4Min
		illuminaryTask3.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				startSouthernShield4();
				southernTaskS3.cancel(true);
				//Prepare for combat! More enemies swarming in!
				sendMsgByRace(1402832, Race.PC_ALL, 0);
				//Hold a little longer and you will survive.
				sendMsgByRace(1402833, Race.PC_ALL, 30000);
            }
        }, 360000)); //...6Min
		illuminaryTask3.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				spawn(702226, 298.13452f, 254.48087f, 295.93027f, (byte) 119); //Southern Defence Charge 03.
				instance.doOnAllPlayers(new Visitor<Player>() {
				    @Override
				    public void visit(Player player) {
						illuminaryWave++;
						stopInstance3(player);
						southernTaskS4.cancel(true);
				    }
			    });
            }
        }, 480000)); //...8Min
	}

	//==========================//
	//== Northern Shield Task ==//
	//==========================//
	protected void startNorthernTask() {
		illuminaryTask4.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				startNorthernShield2();
				northernTaskN1.cancel(true);
				//Prepare for combat! More enemies swarming in!
				sendMsgByRace(1402832, Race.PC_ALL, 0);
				//Hold a little longer and you will survive.
				sendMsgByRace(1402833, Race.PC_ALL, 30000);
				spawn(702227, 212.96484f, 254.4526f, 295.90784f, (byte) 60); //Northern Defence Charge 01.
            }
        }, 120000)); //...2Min
		illuminaryTask4.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				startNorthernShield3();
				northernTaskN2.cancel(true);
				//Prepare for combat! More enemies swarming in!
				sendMsgByRace(1402832, Race.PC_ALL, 0);
				//Hold a little longer and you will survive.
				sendMsgByRace(1402833, Race.PC_ALL, 30000);
				spawn(702228, 212.96484f, 254.4526f, 295.90784f, (byte) 60); //Northern Defence Charge 02.
            }
        }, 240000)); //...4Min
		illuminaryTask4.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				startNorthernShield4();
				northernTaskN3.cancel(true);
				//Prepare for combat! More enemies swarming in!
				sendMsgByRace(1402832, Race.PC_ALL, 0);
				//Hold a little longer and you will survive.
				sendMsgByRace(1402833, Race.PC_ALL, 30000);
            }
        }, 360000)); //...6Min
		illuminaryTask4.add(ThreadPoolManager.getInstance().schedule(new Runnable() {
            @Override
            public void run() {
				spawn(702229, 212.96484f, 254.4526f, 295.90784f, (byte) 60); //Northern Defence Charge 03.
				instance.doOnAllPlayers(new Visitor<Player>() {
				    @Override
				    public void visit(Player player) {
						illuminaryWave++;
						stopInstance4(player);
						northernTaskN4.cancel(true);
				    }
			    });
            }
        }, 480000)); //...8Min
	}

	protected void rushIlluminary(final Npc npc) {
		ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				if (!isInstanceDestroyed) {
					for (Player player: instance.getPlayersInside()) {
						npc.setTarget(player);
						((AbstractAI) npc.getAi2()).setStateIfNot(AIState.WALKING);
						npc.setState(1);
						npc.getMoveController().moveToTargetObject();
						PacketSendUtility.broadcastPacket(npc, new SM_EMOTION(npc, EmotionType.START_EMOTE2, 0, npc.getObjectId()));
					}
				}
			}
		}, 1000);
	}

	protected void shieldControl() {
		if (illuminaryWave == 4) {
			deleteNpc(702010); //Eastern Shield Generator.
			deleteNpc(702011); //Western Shield Generator.
			deleteNpc(702012); //Southern Shield Generator.
			deleteNpc(702013); //Northern Shield Generator.
			deleteNpc(702014); //Eastern Pashid Infiltration Gate.
			deleteNpc(702015); //Western Pashid Infiltration Gate.
			deleteNpc(702016); //Southern Pashid Infiltration Gate.
			deleteNpc(702017); //Northern Pashid Infiltration Gate.
			//The shield is activated and the Pashid Destruction Unit is retreating.
			//The Shield Control Room Teleporter has appeared.
			sendMsgByRace(1402202, Race.PC_ALL, 0);
			//Shield Chamber Teleport Device appeared.
			sendMsgByRace(1403146, Race.PC_ALL, 10000);
			//Shield Complete.
			spawn(702217, 255.31036f, 254.66649f, 455.12018f, (byte) 91);
			//Shield Defence Complete.
			spawn(702287, 255.13590f, 254.21944f, 337.96027f, (byte) 109);
			//Shield Control Room Teleporter.
			spawn(730886, 255.47392f, 293.56177f, 321.18497f, (byte) 89);
			spawn(730886, 255.55742f, 216.03549f, 321.21344f, (byte) 30);
			spawn(730886, 294.20718f, 254.60352f, 295.77290f, (byte) 60);
			spawn(730886, 216.97739f, 254.46160f, 295.77353f, (byte) 0);
		}
	}

	protected void illuminaryToDynatoum(Player player) {
		teleport(player, 266.04742f, 244.20813f, 455.17575f, (byte) 45);
	}

	protected void teleport(float x, float y, float z, byte h) {
		for (Player playerInside: instance.getPlayersInside()) {
			if (playerInside.isOnline()) {
				illuminaryToDynatoum(playerInside);
			}
		}
	}

	protected void teleport(Player player, float x, float y, float z, byte h) {
		TeleportService2.teleportTo(player, mapId, instanceId, x, y, z, h);
	}

	protected void stopInstance1(Player player) {
		shieldControl();
		stopInstanceTask1();
	}

	protected void stopInstance2(Player player) {
		shieldControl();
		stopInstanceTask2();
	}

	protected void stopInstance3(Player player) {
		shieldControl();
		stopInstanceTask3();
	}

	protected void stopInstance4(Player player) {
		shieldControl();
		stopInstanceTask4();
	}

	protected void sendMsg(final String str) {
		instance.doOnAllPlayers(new Visitor<Player>() {
			@Override
			public void visit(Player player) {
				PacketSendUtility.sendWhiteMessageOnCenter(player, str);
			}
		});
	}

	protected void sendMessage(final int msgId, long delay) {
        if (delay == 0) {
            this.sendMsg(msgId);
        } else {
            ThreadPoolManager.getInstance().schedule(new Runnable() {
                public void run() {
                    sendMsg(msgId);
                }
            }, delay);
        }
    }

	protected void sendMsgByRace(final int msg, final Race race, int time) {
		ThreadPoolManager.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				instance.doOnAllPlayers(new Visitor<Player>() {
					@Override
					public void visit(Player player) {
						if (player.getRace().equals(race) || race.equals(Race.PC_ALL)) {
							PacketSendUtility.sendPacket(player, new SM_SYSTEM_MESSAGE(msg));
						}
					}
				});
			}
		}, time);
	}

	protected void sendMovie(Player player, int movie) {
        if (!movies.contains(movie)) {
             movies.add(movie);
             PacketSendUtility.sendPacket(player, new SM_PLAY_MOVIE(0, movie));
        }
    }

	@Override
	public void onLeaveInstance(Player player) {
		removeItems(player);
	}

	@Override
	public void onPlayerLogOut(Player player) {
		removeItems(player);
	}

	public void removeItems(Player player) {
        Storage storage = player.getInventory();
        storage.decreaseByItemId(164000289, storage.getItemCountByItemId(164000289));
		storage.decreaseByItemId(164000290, storage.getItemCountByItemId(164000290));
    }

	@Override
	public void onInstanceDestroy() {
		stopInstanceTask1();
		stopInstanceTask2();
		stopInstanceTask3();
		stopInstanceTask4();
		isInstanceDestroyed = true;
		doors.clear();
		movies.clear();
	}

	protected void stopInstanceTask1() {
        for (Future<?> n : illuminaryTask1) {
            if (n != null) {
                n.cancel(true);
            }
        }
    }

	protected void stopInstanceTask2() {
        for (Future<?> n : illuminaryTask2) {
            if (n != null) {
                n.cancel(true);
            }
        }
    }

	protected void stopInstanceTask3() {
        for (Future<?> n : illuminaryTask3) {
            if (n != null) {
                n.cancel(true);
            }
        }
    }

	protected void stopInstanceTask4() {
        for (Future<?> n : illuminaryTask4) {
            if (n != null) {
                n.cancel(true);
            }
        }
    }

	protected void despawnNpc(Npc npc) {
        if (npc != null) {
            npc.getController().onDelete();
        }
    }

	protected void deleteNpc(int npcId) {
		if (getNpc(npcId) != null) {
			getNpc(npcId).getController().onDelete();
		}
	}

	protected void killNpc(List<Npc> npcs) {
        for (Npc npc: npcs) {
            npc.getController().die();
        }
    }

	protected List<Npc> getNpcs(int npcId) {
		if (!isInstanceDestroyed) {
			return instance.getNpcs(npcId);
		}
		return null;
	}

	public void onExitInstance(Player player) {
		TeleportService2.moveToInstanceExit(player, mapId, player.getRace());
	}

}
