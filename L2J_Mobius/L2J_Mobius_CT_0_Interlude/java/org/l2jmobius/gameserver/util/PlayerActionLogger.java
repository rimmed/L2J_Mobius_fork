/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;
import java.util.Queue;

import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.holders.creature.TimeStamp;

public class PlayerActionLogger
{
	private static final Logger LOGGER = Logger.getLogger("playeraction");
	private static final SimpleDateFormat SHOT_TIMESTAMP_FORMAT = new SimpleDateFormat("HH:mm:ss,SSS");
	private static final long BUFF_DEBUFF_LOG_COOLDOWN = 5000; // 5 seconds
	private static final java.util.Map<Integer, Long> _buffDebuffLogTimes = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long SKILLS_LOG_COOLDOWN = 30000; // 30 seconds
	private static final java.util.Map<Integer, Long> _skillsLogTimes = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long DAMAGE_LOG_COOLDOWN = 100; // 100 ms dedup window
	private static final java.util.Map<Long, Long> _damageLogTimes = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long TARGET_SELECT_LOG_COOLDOWN = 1000; // 1s dedup window for target select
	private static final java.util.Map<Long, Long> _targetSelectLogTimes = new java.util.concurrent.ConcurrentHashMap<>();
	
	static
	{
		Runtime.getRuntime().addShutdownHook(new Thread(() ->
		{
			final Logger logger = Logger.getLogger("playeraction");
			for (java.util.logging.Handler handler : logger.getHandlers())
			{
				handler.flush();
			}
		}, "PlayerActionLogger-ShutdownHook"));
	}
	
	public static void flush()
	{
		final Logger logger = Logger.getLogger("playeraction");
		for (java.util.logging.Handler handler : logger.getHandlers())
		{
			handler.flush();
		}
	}

	private static String getPlayerStatus(Player player)
	{
		final StringBuilder sb = new StringBuilder();
		
		sb.append("[HP:").append(String.format("%.1f", player.getStatus().getCurrentHp())).append("/").append(player.getMaxHp());
		sb.append(" MP:").append(String.format("%.1f", player.getStatus().getCurrentMp())).append("/").append(player.getMaxMp());
		sb.append(" CP:").append(String.format("%.1f", player.getStatus().getCurrentCp())).append("/").append(player.getMaxCp());
		sb.append(" Exp:").append(player.getExp()).append(" Sp:").append(player.getSp());
		sb.append(" Lvl:").append(player.getLevel()).append("]");
		
		sb.append(" P.Atk:").append((int)player.getPAtk(player));
		sb.append(" M.Atk:").append((int)player.getMAtk(player, null));
		sb.append(" P.Def:").append((int)player.getPDef(player));
		sb.append(" M.Def:").append((int)player.getMDef(player, null));
		sb.append(" Accur:").append(player.getAccuracy());
		sb.append("Crit.rate:").append(player.getCriticalHit(player, null));
		sb.append("Evasion:").append(player.getEvasionRate(player));
		sb.append("Atk.Speed:").append(player.getPAtkSpd());
		sb.append("Cast.Speed:").append(player.getMAtkSpd());

		sb.append("STR:").append(player.getSTR());
		sb.append("INT:").append(player.getINT());
		sb.append("DEX:").append(player.getDEX());
		sb.append("CON:").append(player.getCON());
		sb.append("WIT:").append(player.getWIT());

		return sb.toString();
	}
	
	private static String getCooldownInfo(Player player)
	{
		final StringBuilder sb = new StringBuilder();
		final java.util.Map<Integer, TimeStamp> stamps = player.getSkillReuseTimeStamps();
		if ((stamps == null) || stamps.isEmpty())
		{
			sb.append("[CD:None]");
			return sb.toString();
		}
		
		sb.append("[CD:");
		boolean first = true;
		for (java.util.Map.Entry<Integer, TimeStamp> entry : stamps.entrySet())
		{
			final TimeStamp ts = entry.getValue();
			final long remaining = ts.getRemaining();
			if (remaining > 0)
			{
				if (!first)
				{
					sb.append(",");
				}
				sb.append(ts.getSkillId()).append(":").append(remaining);
				first = false;
			}
		}
		
		if (first)
		{
			sb.append("None");
		}
		sb.append("]");
		return sb.toString();
	}
	
	private static String getBuffDebuffInfo(Player player)
	{
		final StringBuilder sb = new StringBuilder();
		
		// Log buffs
		final Queue<BuffInfo> buffs = player.getEffectList().getBuffs();
		if (!buffs.isEmpty())
		{
			sb.append(" Buffs[");
			boolean first = true;
			for (BuffInfo buff : buffs)
			{
				if (!first)
				{
					sb.append(",");
				}
				final Skill skill = buff.getSkill();
				sb.append(skill.getName()).append("(").append(skill.getId()).append(")").append(":").append(buff.getTime()).append("s");
				first = false;
			}
			sb.append("]");
		}
		
		// Log debuffs
		final Queue<BuffInfo> debuffs = player.getEffectList().getDebuffs();
		if (!debuffs.isEmpty())
		{
			sb.append(" Debuffs[");
			boolean first = true;
			for (BuffInfo debuff : debuffs)
			{
				if (!first)
				{
					sb.append(",");
				}
				final Skill skill = debuff.getSkill();
				sb.append(skill.getName()).append("(").append(skill.getId()).append(")").append(":").append(debuff.getTime()).append("s");
				first = false;
			}
			sb.append("]");
		}
		
		return sb.toString();
	}

	private static String getEquipmentInfo(Player player)
	{
		final StringBuilder sb = new StringBuilder();
		final Item head = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_HEAD);
		final Item chest = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);
		final Item gloves = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_GLOVES);
		final Item legs = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEGS);
		final Item feet = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_FEET);
		final Weapon weapon = player.getActiveWeaponItem();
		final Item shield = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LHAND);
		final Item rEar = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_REAR);
		final Item lEar = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LEAR);
		final Item rFinger = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RFINGER);
		final Item lFinger = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_LFINGER);
		final Item neck = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_NECK);

		sb.append("[Weapon:").append(weapon != null ? weapon.getName() : "None");
		sb.append(" Shield:").append(shield != null ? shield.getTemplate().getName() : "None");
		sb.append(" Head:").append(head != null ? head.getTemplate().getName() : "None");
		sb.append(" Chest:").append(chest != null ? chest.getTemplate().getName() : "None");
		sb.append(" Gloves:").append(gloves != null ? gloves.getTemplate().getName() : "None");
		sb.append(" Legs:").append(legs != null ? legs.getTemplate().getName() : "None");
		sb.append(" Feet:").append(feet != null ? feet.getTemplate().getName() : "None");
		sb.append(" Neck:").append(neck != null ? neck.getTemplate().getName() : "None");
		sb.append(" REar:").append(rEar != null ? rEar.getTemplate().getName() : "None");
		sb.append(" LEar:").append(lEar != null ? lEar.getTemplate().getName() : "None");
		sb.append(" RFinger:").append(rFinger != null ? rFinger.getTemplate().getName() : "None");
		sb.append(" LFinger:").append(lFinger != null ? lFinger.getTemplate().getName() : "None");
		sb.append("]");
		return sb.toString();
	}
	
	public static void logSkillsUpdated(Player player)
	{
		if (player == null)
		{
			return;
		}
		
		final String skillsInfo = getSkillsUpdatedInfo(player);
		LOGGER.info(player.getName() + " skills updated: " + skillsInfo + " " + getPlayerStatus(player) + " " + getEquipmentInfo(player) + " " + getSpatialInfo(player, player.isCastingNow()) + " " + getTargetContext(player, player.getTarget()) + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
		flush();
	}

	public static void logTargetSelect(Player player, WorldObject target)
	{
		if (player == null)
		{
			return;
		}
		
		// Dedup: skip if same target selected within cooldown window
		final long key = ((long) player.getObjectId() << 32) | (target != null ? target.getObjectId() : 0);
		final Long lastTime = _targetSelectLogTimes.get(key);
		final long now = System.currentTimeMillis();
		if ((lastTime != null) && ((now - lastTime) < TARGET_SELECT_LOG_COOLDOWN))
		{
			return;
		}
		_targetSelectLogTimes.put(key, now);
		
		final String targetName = target != null ? target.getName() : "None";
		LOGGER.info(player.getName() + " selected target: " + targetName + " " + getPlayerStatus(player) + " " + getEquipmentInfo(player) + " " + getSpatialInfo(player, player.isCastingNow()) + " " + getTargetContext(player, target) + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
		flush();
	}

	public static void logMove(Player player, int x, int y, int z)
	{
		if (player == null)
		{
			return;
		}
		
		// Clear dead targets from movement logs to avoid phantom target tracking
		WorldObject target = player.getTarget();
		String targetContext = getTargetContext(player, target);
		if ((target instanceof Creature) && ((((Creature) target).getCurrentHp() / ((Creature) target).getMaxHp()) == 0.0))
		{
			targetContext = "[Target:None]";
		}
		
		LOGGER.info(player.getName() + " moved to: [" + x + "," + y + "," + z + "] " + getPlayerStatus(player) + " " + getEquipmentInfo(player) + " " + getSpatialInfo(player, player.isCastingNow()) + " " + targetContext + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
		flush();
	}

	public static void logAttack(Player player, WorldObject target)
	{
		if (player == null)
		{
			return;
		}
		final String targetName = target != null ? target.getName() : "None";
		LOGGER.info(player.getName() + " attacked: " + targetName + " " + getPlayerStatus(player) + " " + getEquipmentInfo(player) + " " + getSpatialInfo(player, player.isCastingNow()) + " " + getTargetContext(player, target) + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
		flush();
	}

	public static void logSkillUse(Player player, int skillId, String skillName, WorldObject target)
	{
		if (player == null)
		{
			return;
		}
		final String targetName = target != null ? target.getName() : "None";
		LOGGER.info(player.getName() + " used skill: " + skillName + "[" + skillId + "] on " + targetName + " " + getPlayerStatus(player) + " " + getEquipmentInfo(player) + " " + getSpatialInfo(player, player.isCastingNow()) + " " + getTargetContext(player, target) + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
		flush();
	}

	/**
	 * Logs when a player enables or disables the auto-use toggle for a Soulshot/Spiritshot.
	 * Produces a log line in the format:
	 * PlayerName enabled auto-use for item: ItemName (Item ID: X)
	 * PlayerName disabled auto-use for item: ItemName (Item ID: X)
	 *
	 * @param player  the player toggling auto-use
	 * @param item    the shot item whose auto-use state changed
	 * @param enabled true if auto-use was activated, false if deactivated
	 */
	public static void logShotAutoUse(Player player, Item item, boolean enabled)
	{
		if ((player == null) || (item == null))
		{
			return;
		}
		final String state = enabled ? "enabled" : "disabled";
		LOGGER.info(player.getName() + " " + state + " auto-use for item: " + item.getTemplate().getName() + " (Item ID: " + item.getId() + ")");
		flush();
	}

	/**
	 * Logs a successful item use with the remaining inventory count.
	 * Produces a log line in the format:
	 * PlayerName used item: ItemName [Remaining: Count]
	 *
	 * @param player the player who used the item
	 * @param item   the item that was successfully consumed/used
	 */
	public static void logItemUse(Player player, Item item)
	{
		if ((player == null) || (item == null))
		{
			return;
		}
		LOGGER.info(player.getName() + " used item: " + item.getTemplate().getName() + " [Remaining: " + item.getCount() + "]");
		flush();
	}

	public static void logAction(Player player, String actionName, String details)
	{
		if (player == null)
		{
			return;
		}
		
		// For pickup actions, clear dead target context to avoid ghost monster tracking
		String targetContext = getTargetContext(player, player.getTarget());
		if ((actionName != null) && actionName.toLowerCase().contains("pickup"))
		{
			final WorldObject target = player.getTarget();
			if ((target instanceof Creature) && ((((Creature) target).getCurrentHp() / ((Creature) target).getMaxHp()) == 0.0))
			{
				targetContext = "[Target:None]";
			}
		}
		
		LOGGER.info(player.getName() + " performed action: " + actionName + " - " + details + " " + getPlayerStatus(player) + " " + getEquipmentInfo(player) + " " + getSpatialInfo(player, player.isCastingNow()) + " " + targetContext + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
	}

	public static void logMobKillDrop(Player player, String mobName, String itemName, long itemCount, boolean autoLooted)
	{
		if (player == null)
		{
			return;
		}
		
		// Bug 2: Ensure Cast:false on the kill event line.
		// Force Cast to false: when a mob is killed, the casting action has concluded.
		String spatialInfo = getSpatialInfo(player, false);
		
		LOGGER.info(player.getName() + " killed: " + mobName + " dropped: " + itemName + "[" + itemCount + "]" + (autoLooted ? " auto-looted" : " ground") + " " + getPlayerStatus(player) + " " + getEquipmentInfo(player) + " " + spatialInfo + " " + getTargetContext(player, player.getTarget()) + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
	}

	/**
	 * Logs Soulshot/Spiritshot (including Blessed variants) consumption with the remaining inventory count.
	 * Produces a dedicated log line in the format:
	 * [HH:mm:ss,SSS] PlayerName used Shot_Name | Remaining: Count
	 *
	 * @param player         the player who consumed the shot
	 * @param shotName       the display name of the shot item (e.g. "Blessed Spiritshot (No-Grade)")
	 * @param remainingCount the number of that shot type remaining in inventory after consumption
	 */
	public static void logShotUsage(Player player, String shotName, long remainingCount)
	{
		if (player == null)
		{
			return;
		}
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT)
		{
			timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date());
		}
		LOGGER.info("[" + timestamp + "] " + player.getName() + " used " + shotName + " | Remaining: " + remainingCount);
		flush();
	}

	public static void logDamageTaken(Player player, double damage, Creature attacker, Skill skill)
	{
		if (player == null)
		{
			return;
		}
		final long key = ((long) player.getObjectId() << 32) | (attacker != null ? attacker.getObjectId() : 0);
		final Long lastTime = _damageLogTimes.get(key);
		final long now = System.currentTimeMillis();
		if ((lastTime != null) && ((now - lastTime) < DAMAGE_LOG_COOLDOWN))
		{
			return;
		}
		_damageLogTimes.put(key, now);
		final String attackerName = attacker != null ? attacker.getName() : "unknown";
		final String skillName = skill != null ? skill.getName() : "N/A";
		String damageStr = (int) damage + "";
		if (damage == 0)
		{
			damageStr = "0 (MISS/BLOCK)";
		}
		LOGGER.info(player.getName() + " took damage: " + damageStr + " from: " + attackerName + " skill: " + skillName + " " + getPlayerStatus(player) + " " + getEquipmentInfo(player) + " " + getSpatialInfo(player, player.isCastingNow()) + " " + getTargetContext(player, attacker) + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
		flush();
	}

	public static void logDamageInflicted(Player player, double damage, Creature target, Skill skill)
	{
		if (player == null)
		{
			return;
		}
		final long key = ((long) player.getObjectId() << 32) | (target != null ? target.getObjectId() : 0);
		final Long lastTime = _damageLogTimes.get(key);
		final long now = System.currentTimeMillis();
		if ((lastTime != null) && ((now - lastTime) < DAMAGE_LOG_COOLDOWN))
		{
			return;
		}
		_damageLogTimes.put(key, now);
		final String targetName = target != null ? target.getName() : "unknown";
		final String skillName = skill != null ? skill.getName() : "N/A";
		String damageStr = (int) damage + "";
		if (damage == 0)
		{
			damageStr = "0 (MISS/BLOCK)";
		}
		
		// When logging damage inflicted, the cast has completed; ensure Cast:false in logged state.
		// Force Cast to false: damage infliction means the spell/attack has landed.
		String spatialInfo = getSpatialInfo(player, false);
		
		LOGGER.info(player.getName() + " inflicted damage: " + damageStr + " to: " + targetName + " skill: " + skillName + " " + spatialInfo + " " + getTargetContext(player, target) + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
		flush();
	}

	public static void logBuffsDebuffs(Player player)
	{
		if (player == null)
		{
			return;
		}
		final int playerId = player.getObjectId();
		final Long lastTime = _buffDebuffLogTimes.get(playerId);
		final long now = System.currentTimeMillis();
		if ((lastTime != null) && ((now - lastTime) < BUFF_DEBUFF_LOG_COOLDOWN))
		{
			return;
		}
		_buffDebuffLogTimes.put(playerId, now);
		LOGGER.info(player.getName() + " active effects: " + getBuffDebuffInfo(player) + " " + getPlayerStatus(player) + " " + getEquipmentInfo(player) + " " + getSpatialInfo(player, player.isCastingNow()) + " " + getTargetContext(player, player.getTarget()) + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
	}

	public static void logEffectApplied(Player player, BuffInfo info)
	{
		if ((player == null) || (info == null) || (info.getSkill() == null))
		{
			return;
		}
		final Skill skill = info.getSkill();
		final String effectType = skill.isDebuff() ? "Debuff" : "Buff";
		LOGGER.info(player.getName() + " effect applied: " + effectType + " - " + skill.getName() + "[" + skill.getId() + "] duration:" + info.getTime() + "s " + getPlayerStatus(player) + " " + getEquipmentInfo(player) + " " + getSpatialInfo(player, player.isCastingNow()) + " " + getTargetContext(player, player.getTarget()) + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
	}

	private static String getSkillsUpdatedInfo(Player player)
	{
		final StringBuilder sb = new StringBuilder();
		
		// Active skills
		sb.append("[Active:");
		boolean first = true;
		for (java.util.Map.Entry<Integer, Skill> entry : player.getSkills().entrySet())
		{
			final Skill skill = entry.getValue();
			if (!skill.isPassive())
			{
				if (!first) sb.append(",");
				sb.append(skill.getId()).append("-").append(skill.getLevel());
				first = false;
			}
		}
		sb.append("] ");
		
		// Passive skills
		sb.append("[Passive:");
		first = true;
		for (java.util.Map.Entry<Integer, Skill> entry : player.getSkills().entrySet())
		{
			final Skill skill = entry.getValue();
			if (skill.isPassive())
			{
				if (!first) sb.append(",");
				sb.append(skill.getId()).append("-").append(skill.getLevel());
				first = false;
			}
		}
		sb.append("]");
		
		return sb.toString();
	}
	
	public static void logSkills(Player player)
	{
		if (player == null)
		{
			return;
		}
		
		final int playerId = player.getObjectId();
		final Long lastTime = _skillsLogTimes.get(playerId);
		final long now = System.currentTimeMillis();
		if ((lastTime != null) && ((now - lastTime) < SKILLS_LOG_COOLDOWN))
		{
			return;
		}
		_skillsLogTimes.put(playerId, now);
		
		logSkillsUpdated(player);
	}
	
	private static String getSkillsInfo(Player player)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("Skills[");
		boolean first = true;
		for (java.util.Map.Entry<Integer, Skill> entry : player.getSkills().entrySet())
		{
			final Skill skill = entry.getValue();
			if (!first) sb.append(",");
			sb.append(skill.getName()).append("(").append(skill.getId()).append(")lvl").append(skill.getLevel());
			first = false;
		}
		sb.append("]");
		return sb.toString();
	}
	
	private static String getSpatialInfo(Player player, boolean castingState)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("[Pos:").append(player.getX()).append(",").append(player.getY()).append(",").append(player.getZ());
		sb.append(" Head:").append(player.getHeading());
		sb.append(" Sit:").append(player.isSitting());
		sb.append(" Cast:").append(castingState);
		sb.append(" Atk:").append(player.isAttackingNow());
		sb.append(" Dead:").append(player.isAlikeDead());
		sb.append(" Wt:");
		final double weightPct = (player.getMaxLoad() > 0) ? ((double) player.getCurrentLoad() / player.getMaxLoad() * 100) : 0;
		sb.append(String.format("%.1f", weightPct)).append("%]");
		return sb.toString();
	}
	
	private static String getTargetContext(Player player, WorldObject target)
	{
		if (target == null)
		{
			return "[Target:None]";
		}
		
		final StringBuilder sb = new StringBuilder();
		sb.append("[Target:").append(target.getObjectId());
		
		if (target.isMonster())
		{
			sb.append(" Monster");
		}
		else if (target.isPlayer())
		{
			sb.append(" Player");
		}
		else if (target.isSummon())
		{
			sb.append(" Summon");
		}
		else if (target.isItem())
		{
			sb.append(" Item");
		}
		else if (target.isDoor())
		{
			sb.append(" Door");
		}
		else if (target.isArtefact())
		{
			sb.append(" Artefact");
		}
		else if (target.isNpc())
		{
			sb.append(" Npc");
		}
		else
		{
			sb.append(" Unknown");
		}
		
		sb.append(" Dist:").append(String.format("%.1f", player.calculateDistance3D(target)));
		sb.append(" LoS:").append(player.isInsideRadius3D(target, 1500) && GeoEngine.getInstance().canSeeTarget(player, target));
		
		if (target instanceof Creature)
		{
			final Creature creature = (Creature) target;
			sb.append(" HP:").append(String.format("%.1f", creature.getCurrentHp() / creature.getMaxHp() * 100)).append("%");
			
			// Bug 1: THead should be fetched dynamically.
			int heading = creature.getHeading();
			sb.append(" THead:").append(heading).append("]");
		}
		else
		{
			sb.append("]");
		}
		
		return sb.toString();
	}
	
	private static String getEnvironmentRadar(Player player)
	{
		final StringBuilder sb = new StringBuilder();
		
		int monsterCount = 0;
		int itemCount = 0;
		
		for (Attackable attackable : World.getInstance().getVisibleObjectsInRange(player, Attackable.class, 1000))
		{
			monsterCount++;
		}
		
		for (org.l2jmobius.gameserver.model.item.instance.Item item : World.getInstance().getVisibleObjectsInRange(player, org.l2jmobius.gameserver.model.item.instance.Item.class, 1000))
		{
			itemCount++;
		}
		
		sb.append("[Env:").append("NearbyMon:").append(monsterCount);
		sb.append(" GroundItems:").append(itemCount);
		sb.append(" InCombat:").append(player.isInCombat());
		sb.append("]");
		
		return sb.toString();
	}

	// -------------------------------------------------------------------------
	// 1. ECONOMY: Item Purchases / Sales
	// -------------------------------------------------------------------------

	/**
	 * Logs when a player buys an item from an NPC shop.
	 * Format: [TIMESTAMP] CharacterName BUY Item: [Item Name] (ID: X), Count: Y, Price: Z Adena (NPC: [NPC Name], ID: NPC_ID)
	 *
	 * @param player     the player who bought the item
	 * @param itemName   the name of the purchased item
	 * @param itemId     the ID of the purchased item
	 * @param count      the quantity purchased
	 * @param price      the total price paid
	 * @param npcName    the name of the NPC merchant
	 * @param npcId      the ID of the NPC merchant
	 */
	public static void logItemPurchase(Player player, String itemName, int itemId, long count, long price, String npcName, int npcId)
	{
		if (player == null)
		{
			return;
		}
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT)
		{
			timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date());
		}
		LOGGER.info("[" + timestamp + "] " + player.getName() + " BUY Item: " + itemName + " (ID: " + itemId + "), Count: " + count + ", Price: " + price + " Adena (NPC: " + npcName + ", ID: " + npcId + ")");
		flush();
	}

	/**
	 * Logs when a player sells an item to an NPC shop.
	 * Format: [TIMESTAMP] CharacterName SELL Item: [Item Name] (ID: X), Count: Y, Price: Z Adena (NPC: [NPC Name], ID: NPC_ID)
	 *
	 * @param player     the player who sold the item
	 * @param itemName   the name of the sold item
	 * @param itemId     the ID of the sold item
	 * @param count      the quantity sold
	 * @param price      the total price received
	 * @param npcName    the name of the NPC merchant
	 * @param npcId      the ID of the NPC merchant
	 */
	public static void logItemSell(Player player, String itemName, int itemId, long count, long price, String npcName, int npcId)
	{
		if (player == null)
		{
			return;
		}
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT)
		{
			timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date());
		}
		LOGGER.info("[" + timestamp + "] " + player.getName() + " SELL Item: " + itemName + " (ID: " + itemId + "), Count: " + count + ", Price: " + price + " Adena (NPC: " + npcName + ", ID: " + npcId + ")");
		flush();
	}

	// -------------------------------------------------------------------------
	// 2. CHARACTER PROGRESSION: Skill Learning
	// -------------------------------------------------------------------------

	/**
	 * Logs when a player learns a new skill or levels up an existing one.
	 * Format: [TIMESTAMP] CharacterName learned skill: [Skill Name] (ID: X, Level: Y) [Cost: Z SP/Adena]
	 *
	 * @param player    the player who learned the skill
	 * @param skillName the name of the learned skill
	 * @param skillId   the ID of the learned skill
	 * @param skillLevel the level of the learned skill
	 * @param spCost    the SP cost (0 if none)
	 * @param adenaCost the Adena cost (0 if none)
	 */
	public static void logSkillLearned(Player player, String skillName, int skillId, int skillLevel, int spCost, long adenaCost)
	{
		if (player == null)
		{
			return;
		}
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT)
		{
			timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date());
		}
		final StringBuilder costStr = new StringBuilder();
		if (spCost > 0)
		{
			costStr.append(spCost).append(" SP");
		}
		if (adenaCost > 0)
		{
			if (costStr.length() > 0) costStr.append("/");
			costStr.append(adenaCost).append(" Adena");
		}
		if (costStr.length() == 0)
		{
			costStr.append("0 SP");
		}
		LOGGER.info("[" + timestamp + "] " + player.getName() + " learned skill: " + skillName + " (ID: " + skillId + ", Level: " + skillLevel + ") [Cost: " + costStr.toString() + "]");
		flush();
	}

	// -------------------------------------------------------------------------
	// 3. MOVEMENT & TRAVEL: Teleportation
	// -------------------------------------------------------------------------

	/**
	 * Logs when a player teleports from one location to another.
	 * Format: [TIMESTAMP] CharacterName teleported from [Zone/Coords X,Y,Z] to [Zone/Coords X,Y,Z] via [Method: GK / Scroll / Command]
	 *
	 * @param player     the player who teleported
	 * @param fromX      the source X coordinate
	 * @param fromY      the source Y coordinate
	 * @param fromZ      the source Z coordinate
	 * @param toX        the destination X coordinate
	 * @param toY        the destination Y coordinate
	 * @param toZ        the destination Z coordinate
	 * @param method     the method of teleportation (e.g., "GK", "Scroll", "Command", "Skill")
	 */
	public static void logTeleport(Player player, int fromX, int fromY, int fromZ, int toX, int toY, int toZ, String method)
	{
		if (player == null)
		{
			return;
		}
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT)
		{
			timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date());
		}
		LOGGER.info("[" + timestamp + "] " + player.getName() + " teleported from [" + fromX + "," + fromY + "," + fromZ + "] to [" + toX + "," + toY + "," + toZ + "] via [Method: " + method + "]");
		flush();
	}

	// -------------------------------------------------------------------------
	// 4. LIFE STATE: Death & Resurrection
	// -------------------------------------------------------------------------

	/**
	 * Logs when a player dies (HP reaches 0).
	 * Format: [TIMESTAMP] CharacterName DIED at [Zone/Coords X,Y,Z]. Killer: [Monster/Player Name] (ID: ID)
	 *
	 * @param player     the player who died
	 * @param killerName the name of the killer
	 * @param killerId   the ID of the killer
	 */
	public static void logDeath(Player player, String killerName, int killerId)
	{
		if (player == null)
		{
			return;
		}
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT)
		{
			timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date());
		}
		LOGGER.info("[" + timestamp + "] " + player.getName() + " DIED at [" + player.getX() + "," + player.getY() + "," + player.getZ() + "]. Killer: " + killerName + " (ID: " + killerId + ")");
		flush();
	}

	/**
	 * Logs when a player resurrects (accepts resurrection or respawns at town).
	 * Format: [TIMESTAMP] CharacterName RESURRECTED at [Zone/Coords X,Y,Z] via [Method: Town Respawn / Skill Name / Item Name]
	 *
	 * @param player the player who resurrected
	 * @param method the method of resurrection ("Town Respawn", skill name, item name, etc.)
	 */
	public static void logResurrection(Player player, String method)
	{
		if (player == null)
		{
			return;
		}
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT)
		{
			timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date());
		}
		LOGGER.info("[" + timestamp + "] " + player.getName() + " RESURRECTED at [" + player.getX() + "," + player.getY() + "," + player.getZ() + "] via [Method: " + method + "]");
		flush();
	}
}