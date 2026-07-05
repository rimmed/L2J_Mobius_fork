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
import java.util.List;
import org.l2jmobius.gameserver.model.actor.holders.creature.TimeStamp;
import java.util.Date;
import java.util.logging.Logger;
import java.util.Queue;

import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.enums.creature.Race;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Attackable;

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
	
	// ========================================================================
	// Helper: Build the standard suffix blocks (Player + Nav + Env + CD).
	// These are appended to every log line after the action-specific prefix.
	// ========================================================================
	
	/**
	 * Builds the suffix that is appended to every action log line:
	 * [CharStatus: HP:... MP:... CP:... Exp:... Sp:... Lvl:...]
	 * [CharStats: P.Atk:... M.Atk:... P.Def:... M.Def:... Accur:... Crit.rate:... Evasion:... Atk.Speed:... Cast.Speed:... STR:... INT:... DEX:... CON:... WIT:...]
	 * [Weapon:... Shield:... Head:... Chest:... Gloves:... Legs:... Feet:... Neck:... REar:... LEar:... RFinger:... LFinger:...]
	 * [Pos:X,Y,Z Head:... Sit:... Cast:... Atk:... Dead:... Wt:...%]
	 * [Nav: Dist:... LoS:... THead:...]
	 * [Env:NearbyMon:X GroundItems:Y InCombat:...] [CD:...]
	 */
	private static String buildLogSuffix(Player player, WorldObject targetRef, boolean castingState)
	{
		return getCharStatus(player) + " " + getCharStats(player) + " " + getEquipmentInfo(player) + " " + getSpatialInfo(player, castingState) + " " + getNavInfo(player, targetRef) + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player);
	}
	
	private static String getCharStatus(Player player)
	{
		return "[CharStatus: HP:" + String.format("%.1f", player.getStatus().getCurrentHp()) + "/" + player.getMaxHp() +
			" MP:" + String.format("%.1f", player.getStatus().getCurrentMp()) + "/" + player.getMaxMp() +
			" CP:" + String.format("%.1f", player.getStatus().getCurrentCp()) + "/" + player.getMaxCp() +
			" Exp:" + player.getExp() + " Sp:" + player.getSp() +
			" Lvl:" + player.getLevel() + "]";
	}
	
	private static String getCharStats(Player player)
	{
		return "[CharStats: P.Atk:" + (int) player.getPAtk(player) +
			" M.Atk:" + (int) player.getMAtk(player, null) +
			" P.Def:" + (int) player.getPDef(player) +
			" M.Def:" + (int) player.getMDef(player, null) +
			" Accur:" + player.getAccuracy() +
			" Crit.rate:" + player.getCriticalHit(player, null) +
			" Evasion:" + player.getEvasionRate(player) +
			" Atk.Speed:" + player.getPAtkSpd() +
			" Cast.Speed:" + player.getMAtkSpd() +
			" STR:" + player.getSTR() +
			" INT:" + player.getINT() +
			" DEX:" + player.getDEX() +
			" CON:" + player.getCON() +
			" WIT:" + player.getWIT() + "]";
	}
	
	private static String getCooldownInfo(Player player)
	{
		final StringBuilder sb = new StringBuilder();
		final java.util.Map<Integer, TimeStamp> stamps = player.getSkillReuseTimeStamps();
		if ((stamps == null) || stamps.isEmpty())
		{
			return "[CD:None]";
		}
		
		sb.append("[CD:");
		boolean first = true;
		for (java.util.Map.Entry<Integer, TimeStamp> entry : stamps.entrySet())
		{
			final TimeStamp ts = entry.getValue();
			final long remaining = ts.getRemaining();
			if (remaining > 0)
			{
				if (!first) sb.append(",");
				sb.append(ts.getSkillId()).append(":").append(remaining);
				first = false;
			}
		}
		
		if (first) sb.append("None");
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
				if (!first) sb.append(",");
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
				if (!first) sb.append(",");
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
	
	private static String getSpatialInfo(Player player, boolean castingState)
	{
		return "[Pos:" + player.getX() + "," + player.getY() + "," + player.getZ() +
			" Head:" + player.getHeading() +
			" Sit:" + player.isSitting() +
			" Cast:" + castingState +
			" Atk:" + player.isAttackingNow() +
			" Dead:" + player.isAlikeDead() +
			" Wt:" + String.format("%.1f", (player.getMaxLoad() > 0) ? ((double) player.getCurrentLoad() / player.getMaxLoad() * 100) : 0) + "%]";
	}
	
	// ========================================================================
	// NPC Trait Extraction
	// ========================================================================
	
	/**
	 * Builds the [Type:X] [Weak:A,B] [Resist:C,D] string for an NPC target.
	 */
	private static String getNpcTraits(Npc npc)
	{
		final StringBuilder sb = new StringBuilder();
		
		// Type / Race
		final Race race = npc.getTemplate().getRace();
		sb.append("[Type:").append(race.name()).append("]");
		
		final java.util.List<String> weak = new java.util.ArrayList<>();
		final java.util.List<String> resist = new java.util.ArrayList<>();
		
		final int basePDef = npc.getTemplate().getBasePDef();
		final int baseMDef = npc.getTemplate().getBaseMDef();
		final int basePAtk = npc.getTemplate().getBasePAtk();
		final int baseMAtk = npc.getTemplate().getBaseMAtk();
		
		// Physical/Magical defense assessment
		if (basePDef >= 500) resist.add("STRONG_P_DEF");
		else if (basePDef <= 100) weak.add("WEAK_P_DEF");
		
		if (baseMDef >= 500) resist.add("STRONG_M_DEF");
		else if (baseMDef <= 100) weak.add("WEAK_M_DEF");
		
		// Attack power assessment
		if (basePAtk >= 500) resist.add("STRONG_P_ATK");
		else if (basePAtk <= 100) weak.add("WEAK_P_ATK");
		
		if (baseMAtk >= 500) resist.add("STRONG_M_ATK");
		else if (baseMAtk <= 100) weak.add("WEAK_M_ATK");
		
		// Elemental resistances
		checkElementalTrait(npc.getTemplate().getBaseFireRes(), "FIRE", weak, resist);
		checkElementalTrait(npc.getTemplate().getBaseWindRes(), "WIND", weak, resist);
		checkElementalTrait(npc.getTemplate().getBaseWaterRes(), "WATER", weak, resist);
		checkElementalTrait(npc.getTemplate().getBaseEarthRes(), "EARTH", weak, resist);
		checkElementalTrait(npc.getTemplate().getBaseHolyRes(), "HOLY", weak, resist);
		checkElementalTrait(npc.getTemplate().getBaseDarkRes(), "DARK", weak, resist);
		
		// Weapon vulnerability based on base attack type
		final org.l2jmobius.gameserver.model.item.type.WeaponType atkType = npc.getTemplate().getBaseAttackType();
		if (atkType != null)
		{
			switch (atkType)
			{
				case BOW: weak.add("ARCHER_WEAKNESS"); resist.add("MELEE_RESISTANCE"); break;
				case BLUNT: weak.add("BLUNT_WEAKNESS"); break;
				case DAGGER: weak.add("DAGGER_WEAKNESS"); break;
				case DUAL: case DUALFIST: case DUALDAGGER: weak.add("DUAL_WEAKNESS"); break;
				case FIST: weak.add("FIST_WEAKNESS"); break;
				case POLE: weak.add("POLE_WEAKNESS"); break;
				case SWORD: weak.add("SWORD_WEAKNESS"); break;
				default: weak.add("MELEE_WEAKNESS"); break;
			}
		}
		
		// Race-based trait heuristics
		if (race == Race.UNDEAD) { weak.add("HOLY_WEAKNESS"); resist.add("DARK_RESISTANCE"); }
		else if (race == Race.DEMONIC) { weak.add("HOLY_WEAKNESS"); }
		else if (race == Race.CONSTRUCT) { resist.add("SLEEP_IMMUNITY"); }
		else if (race == Race.PLANT) { weak.add("FIRE_WEAKNESS"); }
		else if (race == Race.ANIMAL) { weak.add("FIRE_WEAKNESS"); }
		else if (race == Race.BEAST) { weak.add("MELEE_WEAKNESS"); }
		
		// Append Weakness list
		sb.append(" [Weak:");
		if (weak.isEmpty()) sb.append("NONE");
		else
		{
			boolean first = true;
			for (String w : weak) { if (!first) sb.append(","); sb.append(w); first = false; }
		}
		sb.append("]");
		
		// Append Resistance list
		sb.append(" [Resist:");
		if (resist.isEmpty()) sb.append("NONE");
		else
		{
			boolean first = true;
			for (String r : resist) { if (!first) sb.append(","); sb.append(r); first = false; }
		}
		sb.append("]");
		
		return sb.toString();
	}
	
	private static void checkElementalTrait(double resValue, String elementName, java.util.List<String> weak, java.util.List<String> resist)
	{
		if (resValue >= 50) resist.add(elementName + "_RESISTANCE");
		else if (resValue <= -50) weak.add(elementName + "_WEAKNESS");
	}
	
	// ========================================================================
	// Target Details Formatting (for the prefix, not the Nav block)
	// ========================================================================
	
	/**
	 * Formats the action target descriptor (no duplication with Nav block).
	 * For NPCs:  Monster_Name (NPC_ID:ID, Lvl:Lvl) | HP:Cur/Max (Pct%) [Type:X] [Weak:A,B] [Resist:C,D]
	 * For Players: PlayerName (Lvl:X)
	 * For others: just name or "None".
	 */
	private static String getActionTarget(WorldObject target)
	{
		if (target == null)
		{
			return "None";
		}
		
		if (target.isNpc())
		{
			final Npc npc = (Npc) target;
			final double currentHp = npc.getCurrentHp();
			final double maxHp = npc.getMaxHp();
			final double hpPct = (maxHp > 0) ? (currentHp / maxHp * 100) : 0;
			return npc.getName() + " (NPC_ID:" + npc.getId() + ", Lvl:" + npc.getLevel() + ") | HP:" + String.format("%.1f", currentHp) + "/" + String.format("%.1f", maxHp) + " (" + String.format("%.1f", hpPct) + "%) " + getNpcTraits(npc);
		}
		else if (target instanceof Creature)
		{
			return ((Creature) target).getName() + " (Lvl:" + ((Creature) target).getLevel() + ")";
		}
		
		return target.getName();
	}
	
	/**
	 * Navigation block — no duplicated NPC name/id/type/weakness data.
	 * Format: [Nav: Dist:XXX.X LoS:true/false THead:XXX]
	 * Returns "[Nav: None]" if target is null.
	 */
	private static String getNavInfo(Player player, WorldObject target)
	{
		if (target == null)
		{
			return "[Nav:None]";
		}
		
		final StringBuilder sb = new StringBuilder();
		sb.append("[Nav: Dist:").append(String.format("%.1f", player.calculateDistance3D(target)));
		sb.append(" LoS:").append(player.isInsideRadius3D(target, 1500) && GeoEngine.getInstance().canSeeTarget(player, target));
		
		if (target instanceof Creature)
		{
			sb.append(" THead:").append(((Creature) target).getHeading());
		}
		
		sb.append("]");
		return sb.toString();
	}
	
	private static String getEnvironmentRadar(Player player)
	{
	    StringBuilder sb = new StringBuilder();
	    List<Npc> nearbyMonsters = World.getInstance().getVisibleObjectsInRange(player, Npc.class, 6000);
	    int monsterCount = nearbyMonsters.size();

	    sb.append("[Env: Monsters Count: ").append(monsterCount).append(", ");
	
	    if (monsterCount > 0)
	    {
	        for (Npc monster : nearbyMonsters)
	        {
	            double distance = player.calculateDistance3D(monster);
	            sb.append("Monster: ").append(monster.getName()).append(" (NPC_ID: ").append(monster.getId()).append(", Lvl: ").append(monster.getLevel()).append(", Distance: ").append(String.format("%.2f", distance)).append("), ");
	        }
	        // Remove the last comma and space
	        sb.setLength(sb.length() - 2);
	    }

	    int itemCount = World.getInstance().getVisibleObjectsInRange(player, Item.class, 1000).size();
	    sb.append(", GroundItems: ").append(itemCount);
	    sb.append(", InCombat: ").append(player.isInCombat());
	    sb.append("]");

	    return sb.toString();
	}
	
	// ========================================================================
	// Skills Updated Helper
	// ========================================================================
	
	private static String getSkillsUpdatedInfo(Player player)
	{
		final StringBuilder sb = new StringBuilder();
		
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
	
	// ========================================================================
	// Public Log Methods
	// ========================================================================
	
	public static void logSkillsUpdated(Player player)
	{
		if (player == null) return;
		final String skillsInfo = getSkillsUpdatedInfo(player);
		LOGGER.info("[" + player.getName() + "] [SkillsUpdated]: " + skillsInfo + " " + buildLogSuffix(player, player.getTarget(), player.isCastingNow()));
		flush();
	}

	public static void logTargetSelect(Player player, WorldObject target)
	{
		if (player == null) return;
		
		final long key = ((long) player.getObjectId() << 32) | (target != null ? target.getObjectId() : 0);
		final Long lastTime = _targetSelectLogTimes.get(key);
		final long now = System.currentTimeMillis();
		if ((lastTime != null) && ((now - lastTime) < TARGET_SELECT_LOG_COOLDOWN)) return;
		_targetSelectLogTimes.put(key, now);
		
		LOGGER.info("[" + player.getName() + "] [selected target]: " + getActionTarget(target) + " " + buildLogSuffix(player, target, player.isCastingNow()));
		flush();
	}

	public static void logMove(Player player, int x, int y, int z)
	{
		if (player == null) return;
		
		WorldObject target = player.getTarget();
		String navInfo = getNavInfo(player, target);
		// Clear dead targets from navigation to avoid phantom target tracking
		if ((target instanceof Creature) && ((((Creature) target).getCurrentHp() / ((Creature) target).getMaxHp()) == 0.0))
		{
			navInfo = "[Nav:None]";
		}
		
		LOGGER.info("[" + player.getName() + "] [moved to]: [" + x + "," + y + "," + z + "] " + getCharStatus(player) + " " + getCharStats(player) + " " + getEquipmentInfo(player) + " " + getSpatialInfo(player, player.isCastingNow()) + " " + navInfo + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
		flush();
	}

	public static void logAttack(Player player, WorldObject target)
	{
		if (player == null) return;
		LOGGER.info("[" + player.getName() + "] [attacked]: " + getActionTarget(target) + " " + buildLogSuffix(player, target, player.isCastingNow()));
		flush();
	}

	public static void logSkillUse(Player player, int skillId, String skillName, WorldObject target)
	{
		if (player == null) return;
		LOGGER.info("[" + player.getName() + "] [used skill]: " + skillName + "[" + skillId + "] on " + getActionTarget(target) + " " + buildLogSuffix(player, target, player.isCastingNow()));
		flush();
	}

	public static void logShotAutoUse(Player player, Item item, boolean enabled)
	{
		if ((player == null) || (item == null)) return;
		final String state = enabled ? "enabled" : "disabled";
		LOGGER.info("[" + player.getName() + "] " + state + " auto-use for item: " + item.getTemplate().getName() + " (Item ID: " + item.getId() + ")");
		flush();
	}

	public static void logItemUse(Player player, Item item)
	{
		if ((player == null) || (item == null)) return;
		LOGGER.info("[" + player.getName() + "] [used item]: " + item.getTemplate().getName() + " [Remaining: " + item.getCount() + "]");
		flush();
	}

	public static void logAction(Player player, String actionName, String details)
	{
		if (player == null) return;
		
		String navInfo = getNavInfo(player, player.getTarget());
		if ((actionName != null) && actionName.toLowerCase().contains("pickup"))
		{
			final WorldObject target = player.getTarget();
			if ((target instanceof Creature) && ((((Creature) target).getCurrentHp() / ((Creature) target).getMaxHp()) == 0.0))
			{
				navInfo = "[Nav:None]";
			}
		}
		
		LOGGER.info("[" + player.getName() + "] [performed action]: " + actionName + " - " + details + " " + getCharStatus(player) + " " + getCharStats(player) + " " + getEquipmentInfo(player) + " " + getSpatialInfo(player, player.isCastingNow()) + " " + navInfo + " " + getEnvironmentRadar(player) + " " + getCooldownInfo(player));
	}

	public static void logMobKillDrop(Player player, String mobName, String itemName, long itemCount, boolean autoLooted)
	{
		if (player == null) return;
		
		String killedDetails = mobName;
		final WorldObject currentTarget = player.getTarget();
		if ((currentTarget != null) && currentTarget.isNpc() && currentTarget.getName().equals(mobName))
		{
			killedDetails = getActionTarget(currentTarget);
		}
		
		LOGGER.info("[" + player.getName() + "] [killed]: " + killedDetails + " [dropped]: " + itemName + "[" + itemCount + "]" + (autoLooted ? " auto-looted" : " ground") + " " + buildLogSuffix(player, player.getTarget(), false));
	}

	public static void logShotUsage(Player player, String shotName, long remainingCount)
	{
		if (player == null) return;
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT)
		{
			timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date());
		}
		LOGGER.info("[" + timestamp + "] [" + player.getName() + "] used " + shotName + " | Remaining: " + remainingCount);
		flush();
	}

	public static void logDamageTaken(Player player, double damage, Creature attacker, Skill skill)
	{
		if (player == null) return;
		
		final long key = ((long) player.getObjectId() << 32) | (attacker != null ? attacker.getObjectId() : 0);
		final Long lastTime = _damageLogTimes.get(key);
		final long now = System.currentTimeMillis();
		if ((lastTime != null) && ((now - lastTime) < DAMAGE_LOG_COOLDOWN)) return;
		_damageLogTimes.put(key, now);
		
		final String skillName = skill != null ? skill.getName() : "N/A";
		String damageStr = (int) damage + "";
		if (damage == 0) damageStr = "0 (MISS/BLOCK)";
		
		LOGGER.info("[" + player.getName() + "] [took damage]: " + damageStr + " from: " + getActionTarget(attacker) + " skill: " + skillName + " " + buildLogSuffix(player, attacker, player.isCastingNow()));
		flush();
	}

	public static void logDamageInflicted(Player player, double damage, Creature target, Skill skill)
	{
		if (player == null) return;
		
		final long key = ((long) player.getObjectId() << 32) | (target != null ? target.getObjectId() : 0);
		final Long lastTime = _damageLogTimes.get(key);
		final long now = System.currentTimeMillis();
		if ((lastTime != null) && ((now - lastTime) < DAMAGE_LOG_COOLDOWN)) return;
		_damageLogTimes.put(key, now);
		
		final String skillName = skill != null ? skill.getName() : "N/A";
		String damageStr = (int) damage + "";
		if (damage == 0) damageStr = "0 (MISS/BLOCK)";
		
		LOGGER.info("[" + player.getName() + "] [inflicted damage]: " + damageStr + " to: " + getActionTarget(target) + " skill: " + skillName + " " + buildLogSuffix(player, target, false));
		flush();
	}

	public static void logBuffsDebuffs(Player player)
	{
		if (player == null) return;
		final int playerId = player.getObjectId();
		final Long lastTime = _buffDebuffLogTimes.get(playerId);
		final long now = System.currentTimeMillis();
		if ((lastTime != null) && ((now - lastTime) < BUFF_DEBUFF_LOG_COOLDOWN)) return;
		_buffDebuffLogTimes.put(playerId, now);
		
		LOGGER.info("[" + player.getName() + "] [active effects]: " + getBuffDebuffInfo(player) + " " + buildLogSuffix(player, player.getTarget(), player.isCastingNow()));
	}

	public static void logEffectApplied(Player player, BuffInfo info)
	{
		if ((player == null) || (info == null) || (info.getSkill() == null)) return;
		final Skill skill = info.getSkill();
		final String effectType = skill.isDebuff() ? "Debuff" : "Buff";
		LOGGER.info("[" + player.getName() + "] [effect applied]: " + effectType + " - " + skill.getName() + "[" + skill.getId() + "] duration:" + info.getTime() + "s " + buildLogSuffix(player, player.getTarget(), player.isCastingNow()));
	}

	public static void logSkills(Player player)
	{
		if (player == null) return;
		final int playerId = player.getObjectId();
		final Long lastTime = _skillsLogTimes.get(playerId);
		final long now = System.currentTimeMillis();
		if ((lastTime != null) && ((now - lastTime) < SKILLS_LOG_COOLDOWN)) return;
		_skillsLogTimes.put(playerId, now);
		logSkillsUpdated(player);
	}
	
	// -------------------------------------------------------------------------
	// 1. ECONOMY: Item Purchases / Sales
	// -------------------------------------------------------------------------

	public static void logItemPurchase(Player player, String itemName, int itemId, long count, long price, String npcName, int npcId)
	{
		if (player == null) return;
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT) { timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date()); }
		LOGGER.info("[" + timestamp + "] [" + player.getName() + "] BUY Item: " + itemName + " (ID: " + itemId + "), Count: " + count + ", Price: " + price + " Adena (NPC: " + npcName + ", ID: " + npcId + ")");
		flush();
	}

	public static void logItemSell(Player player, String itemName, int itemId, long count, long price, String npcName, int npcId)
	{
		if (player == null) return;
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT) { timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date()); }
		LOGGER.info("[" + timestamp + "] [" + player.getName() + "] SELL Item: " + itemName + " (ID: " + itemId + "), Count: " + count + ", Price: " + price + " Adena (NPC: " + npcName + ", ID: " + npcId + ")");
		flush();
	}

	// -------------------------------------------------------------------------
	// 2. CHARACTER PROGRESSION: Skill Learning
	// -------------------------------------------------------------------------

	public static void logSkillLearned(Player player, String skillName, int skillId, int skillLevel, int spCost, long adenaCost)
	{
		if (player == null) return;
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT) { timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date()); }
		final StringBuilder costStr = new StringBuilder();
		if (spCost > 0) costStr.append(spCost).append(" SP");
		if (adenaCost > 0) { if (costStr.length() > 0) costStr.append("/"); costStr.append(adenaCost).append(" Adena"); }
		if (costStr.length() == 0) costStr.append("0 SP");
		LOGGER.info("[" + timestamp + "] [" + player.getName() + "] learned skill: " + skillName + " (ID: " + skillId + ", Level: " + skillLevel + ") [Cost: " + costStr.toString() + "]");
		flush();
	}

	// -------------------------------------------------------------------------
	// 3. MOVEMENT & TRAVEL: Teleportation
	// -------------------------------------------------------------------------

	public static void logTeleport(Player player, int fromX, int fromY, int fromZ, int toX, int toY, int toZ, String method)
	{
		if (player == null) return;
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT) { timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date()); }
		LOGGER.info("[" + timestamp + "] [" + player.getName() + "] teleported from [" + fromX + "," + fromY + "," + fromZ + "] to [" + toX + "," + toY + "," + toZ + "] via [Method: " + method + "]");
		flush();
	}

	// -------------------------------------------------------------------------
	// 4. LIFE STATE: Death & Resurrection
	// -------------------------------------------------------------------------

	public static void logDeath(Player player, String killerName, int killerId)
	{
		if (player == null) return;
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT) { timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date()); }
		LOGGER.info("[" + timestamp + "] [" + player.getName() + "] DIED at [" + player.getX() + "," + player.getY() + "," + player.getZ() + "]. Killer: " + killerName + " (ID: " + killerId + ")");
		flush();
	}

	public static void logResurrection(Player player, String method)
	{
		if (player == null) return;
		final String timestamp;
		synchronized (SHOT_TIMESTAMP_FORMAT) { timestamp = SHOT_TIMESTAMP_FORMAT.format(new Date()); }
		LOGGER.info("[" + timestamp + "] [" + player.getName() + "] RESURRECTED at [" + player.getX() + "," + player.getY() + "," + player.getZ() + "] via [Method: " + method + "]");
		flush();
	}
}