package com.bossingbis.prep;

import com.bossingbis.data.AttackType;
import com.bossingbis.data.EquipmentSlot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

/**
 * Builds the preparation suggestions for a boss trip: gear the boss requires, an inventory, and
 * teleports. It only suggests, before the trip; it never withdraws or equips anything, never
 * suggests prayers, and says nothing about the fight itself.
 */
public final class PrepPlanner
{
	public static final int INVENTORY_SLOTS = 28;
	private static final int ANTIFIRE_DOSES = 2;
	/** A moonlight moth mix has two doses to a prayer potion's four. */
	private static final int PRAYER_MIXES_PER_POTION = 2;
	/** Karambwans to bring for combo eating. */
	private static final int COMBO_FOOD = 4;

	/** For "common drops only": a loot type counts when it drops at least once per this many kills. */
	public static final int COMMON_DROP_KILLS = 64;

	/** Loot types in the order they are suggested, with how they are described. */
	private static final String[][] LOOT_TYPES = {
		{"alchables", "items worth alching"},
		{"herbs", "herbs"},
		{"seeds", "seeds"},
		{"gems", "gems"},
		{"bones", "bones"},
		{"ashes", "demonic ashes"},
	};

	private final PrepData data;

	public PrepPlanner(PrepData data)
	{
		this.data = data;
	}

	public PrepData data()
	{
		return data;
	}

	/**
	 * Worn items the boss needs, known before the gear search so the search can respect them.
	 *
	 * @param ownedGearNames canonical names of all owned equipment (these may differ from the raw
	 *                       names, e.g. "Black slayer helmet (i)" counts as "Slayer helmet (i)")
	 */
	public List<PrepPlan.Requirement> requirements(String boss, OwnedItems owned, Collection<String> ownedGearNames)
	{
		PrepData.BossInfo info = data.boss(boss);
		if (info == null)
		{
			return Collections.emptyList();
		}
		List<PrepPlan.Requirement> out = new ArrayList<>();
		Set<EquipmentSlot> taken = new HashSet<>();
		if (info.required != null)
		{
			for (Map.Entry<String, List<String>> e : info.required.entrySet())
			{
				EquipmentSlot slot = EquipmentSlot.fromKey(e.getKey());
				if (slot != null)
				{
					out.add(new PrepPlan.Requirement(slot, e.getValue(), "needed for " + info.name,
						ownsAny(e.getValue(), owned, ownedGearNames)));
					taken.add(slot);
				}
			}
		}
		for (String key : neededLocations(info))
		{
			PrepData.LocationNeeds needs = data.locationNeeds().get(key);
			if (needs.required == null)
			{
				continue;
			}
			for (Map.Entry<String, List<String>> e : needs.required.entrySet())
			{
				EquipmentSlot slot = EquipmentSlot.fromKey(e.getKey());
				if (slot != null && taken.add(slot))
				{
					out.add(new PrepPlan.Requirement(slot, e.getValue(), "needed in " + key,
						ownsAny(e.getValue(), owned, ownedGearNames)));
				}
			}
		}
		PrepData.Supplies s = data.supplies();
		if ("wyvern".equals(info.dragonfire))
		{
			out.add(new PrepPlan.Requirement(EquipmentSlot.SHIELD, s.wyvernShields,
				"protects against wyvern breath", ownsAny(s.wyvernShields, owned, ownedGearNames)));
		}
		else if ("dragon".equals(info.dragonfire) && firstOwned(s.superAntifire, owned) == null)
		{
			out.add(new PrepPlan.Requirement(EquipmentSlot.SHIELD, s.dragonfireShields,
				"protects against dragonfire (not needed with a super antifire)",
				ownsAny(s.dragonfireShields, owned, ownedGearNames)));
		}
		return out;
	}

	/**
	 * Worn items recommended for this boss for reasons the DPS maths can't see, with how much DPS
	 * each may cost.
	 */
	public List<PrepPlan.Preference> preferences(String boss)
	{
		PrepData.BossInfo info = data.boss(boss);
		if (info == null || info.prefer == null)
		{
			return Collections.emptyList();
		}
		List<PrepPlan.Preference> out = new ArrayList<>();
		for (PrepData.Preference p : info.prefer)
		{
			EquipmentSlot slot = EquipmentSlot.fromKey(p.slot);
			if (slot != null && p.items != null && !p.items.isEmpty())
			{
				out.add(new PrepPlan.Preference(slot, p.items, p.withinPercent, p.reason));
			}
		}
		return out;
	}

	/**
	 * The least the worn gear must weigh for this boss, in grams; 0 for no limit.
	 */
	public int minWeight(String boss)
	{
		PrepData.BossInfo info = data.boss(boss);
		return info == null ? 0 : (int) Math.round(info.minWeight * 1000);
	}

	/**
	 * Whether the boss poisons or envenoms you.
	 */
	public boolean poisons(String boss)
	{
		PrepData.BossInfo info = data.boss(boss);
		return info != null && info.venom;
	}

	/**
	 * Everything the inventory plan depends on.
	 */
	@Value
	@Builder
	public static class Request
	{
		String boss;
		AttackType bestType;
		@Builder.Default
		List<PrepPlan.Requirement> requirements = Collections.emptyList();
		OwnedItems owned;
		/**
		 * Gear switches for the fight's other phases or monsters, carried in the inventory. Each
		 * is an owned item name and what it's for.
		 */
		@Builder.Default
		List<PrepPlan.Switch> switches = Collections.emptyList();
		/** Also carry the swaps the wiki recommends for part of the fight, when owned. */
		@Builder.Default
		boolean wikiSwitches = true;
		int prayerPotions;
		@Builder.Default
		PrayerRestore prayerRestore = PrayerRestore.POTIONS_FIRST;
		int combatPotions;
		boolean fillFood;
		/** The monster being fought, for its drops. */
		String monsterName;
		/** Its version, such as "Awakened", for items only that version needs. */
		String monsterVersion;
		/** Suggest tools for loot that drops at least once per this many kills; 0 is off. */
		int lootMaxKills;
		int monsterHitpoints;
		/** The weapons in the planned sets, so they are not suggested again as a spec weapon. */
		@Builder.Default
		Set<String> plannedWeapons = Collections.emptySet();
		/** Names of the items in the suggested worn set, so they aren't also listed to carry. */
		@Builder.Default
		Set<String> wornNames = Collections.emptySet();
		boolean suggestSpecWeapon;
		/** Food picked in the panel to use first, or null for the best owned. */
		String preferredFood;
		/** The suggested set wears a charged Serpentine helm, so no anti-venom is needed. */
		boolean venomImmune;
		/** The player's spellbook (standard, ancient, lunar or arceuus), or null if unknown. */
		String spellbook;
		/** The player's Magic level, for teleport spells. */
		int magicLevel;
		/** Finished quests that unlock teleport spells, as RuneLite Quest names. */
		@Builder.Default
		Set<String> finishedQuests = Collections.emptySet();
		/** The elite Lumbridge Diary is done, so fairy rings need no staff. */
		boolean fairyRingsWithoutStaff;
	}

	public PrepPlan plan(Request request)
	{
		AttackType bestType = request.getBestType();
		OwnedItems owned = request.getOwned();
		String monsterName = request.getMonsterName();
		PrepData.BossInfo info = data.boss(request.getBoss());
		PrepData.Supplies s = data.supplies();
		List<PrepPlan.InventoryLine> lines = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		Set<Integer> ids = new HashSet<>();
		int slots = 0;

		RunePlan runes = new RunePlan();
		List<PrepData.InventoryItem> carried = new ArrayList<>();
		if (info != null && info.inventory != null)
		{
			carried.addAll(info.inventory);
		}
		List<String> locationTips = new ArrayList<>();
		List<String> locationKeys = locationKeys(info);
		List<String> needed = neededLocations(info);
		for (String key : locationKeys)
		{
			PrepData.LocationNeeds needs = data.locationNeeds().get(key);
			if (needs == null)
			{
				continue;
			}
			if (needed.contains(key))
			{
				if (needs.inventory != null)
				{
					carried.addAll(needs.inventory);
				}
				if (needs.tips != null)
				{
					locationTips.addAll(needs.tips);
				}
			}
			else if (needs.summary != null)
			{
				locationTips.add("If you go to " + key + ": " + needs.summary + ".");
			}
		}
		List<PrepData.InventoryItem> wikiSwitches = new ArrayList<>();
		String version = request.getMonsterVersion() == null ? "" : request.getMonsterVersion().toLowerCase(Locale.ROOT);
		carried.removeIf(item -> item.onlyFor != null && !version.contains(item.onlyFor.toLowerCase(Locale.ROOT)));
		for (PrepData.InventoryItem item : carried)
		{
			if (item.switchItem)
			{
				// after the gear switches, below
				wikiSwitches.add(item);
				continue;
			}
			String name = item.exact ? firstOwnedExact(item.names, owned) : firstOwned(item.names, owned);
			// already worn in the planned set (say, a crossbow for the mith grapple): nothing to carry
			boolean worn = item.names.stream().anyMatch(request.getWornNames()::contains);
			if ((name == null && item.optional) || worn)
			{
				continue;
			}
			int have = name != null ? owned.count(name, item.exact) : 0;
			// a stackable item brings the whole stack in its one slot
			int quantity = item.stackable ? Math.max(1, have) : 1;
			lines.add(new PrepPlan.InventoryLine(name != null ? name : item.names.get(0), quantity, have, item.note,
				!item.optional, name != null ? owned.anyId(name, item.exact) : -1, item.stackable));
			collect(name, item.exact, owned, ids);
			slots++;
		}

		for (PrepPlan.Switch sw : request.getSwitches())
		{
			if (!listed(lines, sw.getName()))
			{
				slots += addLine(lines, ids, owned, sw.getName(), 1, sw.getNote(), false);
			}
		}

		// swaps the wiki recommends for part of the fight: only ones you own, and not already planned
		// (an uncharged, empty or broken copy can't be fought with, so it isn't carried)
		OwnedItems usableGear = owned.usableGear();
		for (PrepData.InventoryItem item : request.isWikiSwitches() ? wikiSwitches : List.<PrepData.InventoryItem>of())
		{
			String name = item.exact ? firstOwnedExact(item.names, usableGear) : firstOwned(item.names, usableGear);
			boolean worn = item.names.stream().anyMatch(request.getWornNames()::contains);
			if (worn || item.names.stream().anyMatch(n -> listed(lines, n)))
			{
				continue;
			}
			if (name == null)
			{
				String unusable = item.exact ? firstOwnedExact(item.names, owned) : firstOwned(item.names, owned);
				if (unusable != null)
				{
					notes.add("Your " + unusable + " needs charging or repairing before it can come as a wiki switch ("
						+ item.note + ").");
				}
				continue;
			}
			slots += item.stackable
				? addStack(lines, ids, usableGear, name, "wiki switch: " + item.note)
				: addLine(lines, ids, usableGear, name, 1, "wiki switch: " + item.note, false);
		}

		if (info != null && "dragon".equals(info.dragonfire))
		{
			String superAntifire = firstOwned(s.superAntifire, owned);
			String antifire = firstOwned(s.antifire, owned);
			if (superAntifire != null)
			{
				slots += addLine(lines, ids, owned, superAntifire, ANTIFIRE_DOSES, "protects against dragonfire", false);
			}
			else if (antifire != null)
			{
				slots += addLine(lines, ids, owned, antifire, ANTIFIRE_DOSES, "use with a dragonfire-protecting shield", false);
			}
			else
			{
				notes.add("No antifire potions found. A dragonfire-protecting shield alone does not block all dragonfire.");
			}
		}

		if (info != null && info.venom && !request.isVenomImmune())
		{
			String anti = firstOwned(s.antivenom, owned);
			if (anti != null)
			{
				boolean poisonOnly = s.poisonOnly != null && s.poisonOnly.contains(anti);
				slots += addLine(lines, ids, owned, anti, 1, "this boss can poison or envenom you"
					+ (poisonOnly ? "; this cures poison but not venom" : ""), false);
			}
			else
			{
				notes.add("No anti-venom or antidote found; this boss can poison or envenom you.");
			}
		}

		if (request.getPrayerPotions() > 0)
		{
			slots += addPrayer(lines, ids, owned, request.getPrayerPotions(), request.getPrayerRestore(), notes);
		}

		int combatPotions = request.getCombatPotions();
		boolean magic = bestType == AttackType.MAGIC;
		List<String> stylePotions = bestType == null ? Collections.emptyList()
			: bestType == AttackType.RANGED ? s.rangedPotions
			: magic ? s.magicPotions
			: s.meleePotions;
		String styleName = magic ? "magic" : bestType == null ? "" : bestType.isMelee() ? "melee"
			: bestType.getDisplayName().toLowerCase(Locale.ROOT);
		String potion = firstOwned(stylePotions, owned);
		if (potion != null && combatPotions > 0)
		{
			slots += addLine(lines, ids, owned, potion, combatPotions, "boosts your " + styleName, false);
		}
		else if (bestType != null && bestType.isMelee() && combatPotions > 0)
		{
			slots += addMeleeFallback(lines, ids, owned, combatPotions, notes);
		}
		else if (bestType != null && combatPotions > 0)
		{
			notes.add("No " + styleName + " boosting potion found.");
		}
		if (magic)
		{
			String heart = firstOwned(s.magicBoostItems, owned);
			if (heart != null)
			{
				slots += addLine(lines, ids, owned, heart, 1, "boosts Magic", false);
			}
		}

		if (request.isSuggestSpecWeapon() && request.getMonsterHitpoints() >= s.specMinHitpoints && s.specWeapons != null
			&& (info == null || !info.noDrainSpec))
		{
			OwnedItems specGear = owned.usableGear();
			for (PrepData.SpecWeapon spec : s.specWeapons)
			{
				if (specGear.owns(spec.name) && !request.getPlannedWeapons().contains(spec.name) && !listed(lines, spec.name))
				{
					slots += addLine(lines, ids, specGear, spec.name, 1, "special attack: " + spec.note, false);
					break;
				}
			}
		}

		if (request.getLootMaxKills() > 0)
		{
			slots += addLootTools(lines, ids, owned, monsterName, request.getLootMaxKills(), runes);
		}

		List<String> tips = new ArrayList<>(locationTips);
		if (info != null && info.tips != null)
		{
			tips.addAll(info.tips);
		}
		slots += addTeleports(lines, ids, owned, locationKeys, tips, request, runes);
		slots += addRunes(lines, ids, owned, runes, request, notes);

		if (request.isFillFood())
		{
			boolean comboEat = info != null && info.comboEat || request.getMonsterHitpoints() >= s.specMinHitpoints;
			slots += addFood(lines, ids, owned, INVENTORY_SLOTS - slots, comboEat, request.getPreferredFood(), notes);
		}
		if (slots > INVENTORY_SLOTS)
		{
			notes.add("This plan needs " + slots + " slots; leave out some switches or supplies.");
		}

		for (PrepPlan.Requirement r : request.getRequirements())
		{
			if (!r.isOwned())
			{
				notes.add(0, "Missing " + r.getSlot().getDisplayName().toLowerCase(Locale.ROOT) + " item: "
					+ String.join(" / ", r.getAccepted()) + " (" + r.getReason() + ").");
			}
		}
		for (PrepPlan.InventoryLine line : lines)
		{
			if (line.isRequired() && line.getOwned() == 0)
			{
				notes.add(0, "Missing " + line.getName() + " (" + line.getNote() + ").");
			}
		}

		return new PrepPlan(request.getRequirements(), lines, teleports(locationKeys, owned, request), notes, tips, slots,
			Collections.unmodifiableSet(ids), foodOptions(owned));
	}

	/**
	 * Items that collect or make use of what the monster drops, for the ones the player owns.
	 */
	private int addLootTools(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned,
		String monsterName, int lootMaxKills, RunePlan runes)
	{
		Map<String, Integer> drops = data.drops(monsterName);
		PrepData.Supplies s = data.supplies();
		int slots = 0;
		for (String[] type : LOOT_TYPES)
		{
			Integer kills = drops.get(type[0]);
			if (kills == null || kills > lootMaxKills || s.lootTools == null)
			{
				continue;
			}
			String often = kills <= 1 ? "every kill" : "about 1 in " + kills + " kills";
			if ("alchables".equals(type[0]))
			{
				// the runes go in the rune pouch with any others; without one, nature runes and a fire staff
				boolean pouch = s.runePouches != null && firstOwned(new ArrayList<>(s.runePouches.keySet()), owned) != null;
				String purpose = "High Alchemy: " + monsterName + " drops " + type[1] + ", " + often;
				runes.uses.put(pouch ? purpose : purpose + "; bring a fire staff",
					pouch ? s.alchemyRunes : Map.of("Nature rune", s.alchemyRunes.get("Nature rune")));
				continue;
			}
			String tool = firstOwned(s.lootTools.get(type[0]), owned);
			if (tool != null && !listed(lines, tool))
			{
				String note = monsterName + " drops " + type[1] + " (" + often + ")";
				slots += addLine(lines, ids, owned, tool, 1, note, false);
			}
		}
		return slots;
	}

	/**
	 * Prayer potions and moonlight moth mixes in the chosen order, the second making up any
	 * shortfall of the first. Two mixes count as one prayer potion.
	 */
	private int addPrayer(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned, int wanted,
		PrayerRestore mode, List<String> notes)
	{
		PrepData.Supplies s = data.supplies();
		int[] missing = {wanted};
		int slots = 0;
		if (mode == PrayerRestore.MIXES_FIRST)
		{
			slots += addPrayerItems(lines, ids, owned, s.prayerMixes, PRAYER_MIXES_PER_POTION, missing);
		}
		slots += addPrayerItems(lines, ids, owned, s.prayerPotions, 1, missing);
		if (mode == PrayerRestore.POTIONS_FIRST)
		{
			slots += addPrayerItems(lines, ids, owned, s.prayerMixes, PRAYER_MIXES_PER_POTION, missing);
		}
		if (missing[0] == wanted)
		{
			boolean mixesLeftOut = mode == PrayerRestore.POTIONS_ONLY && firstOwned(s.prayerMixes, owned) != null;
			notes.add(mode != PrayerRestore.POTIONS_ONLY
				? "No prayer potions, super restores, sanfew serums or moonlight moth mixes found."
				: mixesLeftOut
				? "No prayer potions, super restores or sanfew serums found. You have moonlight moth mixes; set "
					+ "Prayer restore to include them."
				: "No prayer potions, super restores or sanfew serums found.");
		}
		return slots;
	}

	/**
	 * Melee potions when there's no super combat potion: super attack and super strength potions,
	 * else a combat potion, else attack and strength potions. A missing super potion is made up
	 * with the plain one for that stat.
	 */
	private int addMeleeFallback(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned,
		int count, List<String> notes)
	{
		PrepData.Supplies s = data.supplies();
		String superAttack = firstOwned(s.attackPotions, owned);
		String superStrength = firstOwned(s.strengthPotions, owned);
		String combat = firstOwned(s.weakMeleePotions, owned);
		String attack;
		String strength;
		if (superAttack != null || superStrength != null)
		{
			attack = superAttack != null ? superAttack : firstOwned(s.weakAttackPotions, owned);
			strength = superStrength != null ? superStrength : firstOwned(s.weakStrengthPotions, owned);
		}
		else if (combat != null)
		{
			return addLine(lines, ids, owned, combat, count, "boosts your melee", false);
		}
		else
		{
			attack = firstOwned(s.weakAttackPotions, owned);
			strength = firstOwned(s.weakStrengthPotions, owned);
		}
		int slots = 0;
		if (attack != null)
		{
			slots += addLine(lines, ids, owned, attack, count, "boosts your Attack", false);
		}
		if (strength != null)
		{
			slots += addLine(lines, ids, owned, strength, count, "boosts your Strength", false);
		}
		if (attack == null && strength == null)
		{
			notes.add("No melee boosting potion found.");
		}
		return slots;
	}

	/**
	 * @param perPotion how many of these items stand in for one prayer potion
	 * @param missing   prayer potions still wanted, updated in place
	 */
	private static int addPrayerItems(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned,
		List<String> names, int perPotion, int[] missing)
	{
		int slots = 0;
		if (names == null)
		{
			return 0;
		}
		for (String name : names)
		{
			if (missing[0] > 0 && owned.owns(name))
			{
				String note = perPotion == 1 ? "restores prayer points"
					: "restores prayer points; " + perPotion + " count as one prayer potion";
				int take = addLine(lines, ids, owned, name, missing[0] * perPotion, note, false);
				slots += take;
				missing[0] -= (take + perPotion - 1) / perPotion;
			}
		}
		return slots;
	}

	/**
	 * The best route to the boss the player can use (the first location that has one), so the
	 * plan doesn't carry several ways there.
	 */
	private int addTeleports(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned,
		List<String> keys, List<String> tips, Request request, RunePlan runes)
	{
		for (String key : keys)
		{
			List<PrepData.Teleport> list = data.locations().get(key);
			if (list == null)
			{
				continue;
			}
			int added = addRoute(lines, ids, owned, key, list, tips, request, runes);
			if (added >= 0)
			{
				return added;
			}
		}
		return 0;
	}

	/**
	 * The runes the plan carries, by what they're for, so they share one rune pouch.
	 */
	private static final class RunePlan
	{
		/** Purpose to the runes it needs per cast, in the order they were added. */
		final Map<String, Map<String, Integer>> uses = new LinkedHashMap<>();
		boolean isEmpty()
		{
			return uses.isEmpty();
		}

		List<String> runeTypes()
		{
			Set<String> types = new LinkedHashSet<>();
			uses.values().forEach(r -> types.addAll(r.keySet()));
			return new ArrayList<>(types);
		}
	}

	/**
	 * The runes: in your best rune pouch when you own one, with what each is for; any types it can't
	 * hold, or all of them without a pouch, as stacks of their own. A worn elemental staff covers its
	 * runes, and combination runes stand in for the runes they count as.
	 */
	private int addRunes(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned, RunePlan runes,
		Request request, List<String> notes)
	{
		if (runes.isEmpty())
		{
			return 0;
		}
		PrepData.Supplies s = data.supplies();
		String pouch = s.runePouches == null ? null : firstOwned(new ArrayList<>(s.runePouches.keySet()), owned);
		Map<String, String> sources = runeSources(runes.runeTypes(), request, owned);
		// what's carried, in order, and what each carried rune is for
		Map<String, Set<String>> carried = new LinkedHashMap<>();
		runes.uses.forEach((purpose, r) ->
		{
			List<String> missing = new ArrayList<>();
			for (String rune : r.keySet())
			{
				String source = sources.get(rune);
				if (source == null)
				{
					missing.add(shortName(rune));
				}
				else if (!source.isEmpty())
				{
					carried.computeIfAbsent(source, k -> new LinkedHashSet<>()).add(purpose);
				}
			}
			if (!missing.isEmpty())
			{
				notes.add("No " + joinAnd(missing) + " runes found for " + purpose + ".");
			}
		});
		List<String> types = new ArrayList<>(carried.keySet());
		List<String> loose = types;
		int slots = 0;
		if (pouch != null)
		{
			int capacity = s.runePouches.get(pouch);
			List<String> inPouch = types.subList(0, Math.min(capacity, types.size()));
			loose = types.subList(inPouch.size(), types.size());
			List<String> parts = new ArrayList<>();
			runes.uses.forEach((purpose, r) ->
			{
				Set<String> these = new LinkedHashSet<>();
				List<String> fromStaff = new ArrayList<>();
				for (String rune : r.keySet())
				{
					String source = sources.get(rune);
					if (source != null && source.isEmpty())
					{
						fromStaff.add(shortName(rune));
					}
					else if (source != null && inPouch.contains(source))
					{
						these.add(shortName(source));
					}
				}
				if (!these.isEmpty())
				{
					parts.add(joinAnd(new ArrayList<>(these)) + " runes (" + purpose
						+ (fromStaff.isEmpty() ? "" : "; your staff gives the " + joinAnd(fromStaff)) + ")");
				}
			});
			if (!parts.isEmpty() && !listed(lines, pouch))
			{
				slots += addLine(lines, ids, owned, pouch, 1, "with " + joinAnd(parts), false);
			}
		}
		for (String rune : loose)
		{
			if (!listed(lines, rune))
			{
				slots += addStack(lines, ids, owned, rune, "for " + String.join("; ", carried.get(rune)));
			}
		}
		return slots;
	}

	private static String shortName(String rune)
	{
		return rune.replace(" rune", "").toLowerCase(Locale.ROOT);
	}

	private static String joinAnd(List<String> parts)
	{
		return parts.size() <= 1 ? String.join("", parts)
			: String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
	}

	/**
	 * The teleport spell a route starts with, or null when it isn't one.
	 */
	private PrepData.TeleportSpell spellFor(String text)
	{
		Map<String, PrepData.TeleportSpell> spells = data.supplies().teleportSpells;
		if (text == null || spells == null)
		{
			return null;
		}
		for (Map.Entry<String, PrepData.TeleportSpell> e : spells.entrySet())
		{
			if (text.startsWith(e.getKey()))
			{
				return e.getValue();
			}
		}
		return null;
	}

	/**
	 * Whether the player can cast a teleport spell now: on its spellbook, with the Magic level, the
	 * quest done, and the runes for one cast (a worn elemental staff or combination runes count).
	 */
	private boolean canCast(PrepData.TeleportSpell spell, Request request, OwnedItems owned)
	{
		if (request.getSpellbook() == null || !request.getSpellbook().equals(spell.spellbook)
			|| request.getMagicLevel() < spell.level
			|| (spell.quest != null && !request.getFinishedQuests().contains(spell.quest)))
		{
			return false;
		}
		Map<String, String> sources = runeSources(spell.runes.keySet(), request, owned);
		// a combination rune counts as one of each of its runes, so it needs the most of either
		Map<String, Integer> needed = new HashMap<>();
		for (Map.Entry<String, Integer> rune : spell.runes.entrySet())
		{
			String source = sources.get(rune.getKey());
			if (source == null)
			{
				return false;
			}
			if (!source.isEmpty())
			{
				needed.merge(source, rune.getValue(), Math::max);
			}
		}
		for (Map.Entry<String, Integer> e : needed.entrySet())
		{
			if (owned.count(e.getKey(), true) < e.getValue())
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * The runes the planned worn set provides without carrying any: an elemental staff's.
	 */
	private Set<String> staffRunes(Request request)
	{
		Set<String> out = new HashSet<>();
		Map<String, List<String>> staves = data.supplies().runeStaves;
		if (staves != null)
		{
			for (Map.Entry<String, List<String>> e : staves.entrySet())
			{
				if (request.getWornNames().contains(e.getKey()))
				{
					out.addAll(e.getValue());
				}
			}
		}
		return out;
	}

	/**
	 * Which owned rune to carry for each needed rune: "" when a worn staff provides it; a
	 * combination rune that covers two of the needed runes (one pouch slot instead of two); the rune
	 * itself; any combination rune that covers it; null when the player has none.
	 */
	private Map<String, String> runeSources(Collection<String> needed, Request request, OwnedItems owned)
	{
		Map<String, String> chosen = new HashMap<>();
		Set<String> free = staffRunes(request);
		List<String> remaining = new ArrayList<>();
		for (String rune : needed)
		{
			if (free.contains(rune))
			{
				chosen.put(rune, "");
			}
			else
			{
				remaining.add(rune);
			}
		}
		Map<String, List<String>> combos = data.supplies().comboRunes;
		combos = combos == null ? Collections.emptyMap() : combos;
		for (Map.Entry<String, List<String>> combo : combos.entrySet())
		{
			List<String> covered = new ArrayList<>(combo.getValue());
			covered.retainAll(remaining);
			if (covered.size() >= 2 && owned.count(combo.getKey(), true) > 0)
			{
				covered.forEach(rune -> chosen.put(rune, combo.getKey()));
				remaining.removeAll(covered);
			}
		}
		for (String rune : remaining)
		{
			String source = owned.count(rune, true) > 0 ? rune : null;
			for (Map.Entry<String, List<String>> combo : combos.entrySet())
			{
				if (source == null && combo.getValue().contains(rune) && owned.count(combo.getKey(), true) > 0)
				{
					source = combo.getKey();
				}
			}
			chosen.put(rune, source);
		}
		Map<String, String> out = new LinkedHashMap<>();
		needed.forEach(rune -> out.put(rune, chosen.get(rune)));
		return out;
	}

	private static String fairyRingCode(String text)
	{
		return text != null && text.startsWith("Fairy ring ") && text.length() >= 14 ? text.substring(11, 14) : null;
	}

	/**
	 * The fairy ring staff to carry: "" when none is needed (elite Lumbridge Diary), null when the
	 * player has none.
	 */
	private String fairyRingStaff(Request request, OwnedItems owned)
	{
		return request.isFairyRingsWithoutStaff() ? "" : firstOwned(data.supplies().fairyRingStaffs, owned);
	}

	/**
	 * The best way the player has to reach a fairy ring, or null.
	 */
	private PrepData.Teleport fairyRingAccess(OwnedItems owned)
	{
		List<PrepData.Teleport> access = data.supplies().fairyRingAccess;
		if (access != null)
		{
			for (PrepData.Teleport t : access)
			{
				if (ownedTeleport(t.items, owned) != null)
				{
					return t;
				}
			}
		}
		return null;
	}

	/**
	 * Whether the player can use a route now: an owned teleport item, a spell they can cast, or a
	 * fairy ring they can reach and use; null for routes that need nothing to carry.
	 */
	private Boolean usable(PrepData.Teleport t, Request request, OwnedItems owned)
	{
		if (t.items != null && !t.items.isEmpty())
		{
			return ownedTeleport(t.items, owned) != null;
		}
		PrepData.TeleportSpell spell = spellFor(t.text);
		if (spell != null)
		{
			return canCast(spell, request, owned);
		}
		if (fairyRingCode(t.text) != null)
		{
			return fairyRingStaff(request, owned) != null && fairyRingAccess(owned) != null;
		}
		return null;
	}

	/**
	 * The best route to a place the player can use, in the data's best-first order: carries the
	 * teleport item, plans the spell's runes, or carries the fairy ring staff and the way to a ring.
	 * Worn teleports become a tip. Returns the slots used, or -1 when no route can be planned.
	 */
	private int addRoute(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned, String key,
		List<PrepData.Teleport> list, List<String> tips, Request request, RunePlan runes)
	{
		PrepData.Supplies s = data.supplies();
		for (PrepData.Teleport t : list)
		{
			if (t.items != null && !t.items.isEmpty())
			{
				String name = ownedTeleport(t.items, owned);
				if (name == null)
				{
					continue;
				}
				if (matchesAny(name, s.wornTeleports))
				{
					tips.add("Wear your " + name + " to teleport to " + key + ".");
					return 0;
				}
				if (listed(lines, name))
				{
					return 0;
				}
				String note = "teleport to " + key;
				return matchesAny(name, s.stackableTeleports)
					? addStack(lines, ids, owned, name, note)
					: addLine(lines, ids, owned, name, 1, note, false);
			}
			PrepData.TeleportSpell spell = spellFor(t.text);
			if (spell != null)
			{
				if (!canCast(spell, request, owned))
				{
					continue;
				}
				runes.uses.put("teleport to " + key, spell.runes);
				return 0;
			}
			String code = fairyRingCode(t.text);
			if (code != null)
			{
				String staff = fairyRingStaff(request, owned);
				PrepData.Teleport access = fairyRingAccess(owned);
				if (staff == null || access == null)
				{
					continue;
				}
				int slots = 0;
				if (!staff.isEmpty() && !listed(lines, staff) && !request.getWornNames().contains(staff))
				{
					slots += addLine(lines, ids, owned, staff, 1, "to use the fairy ring " + code + " to " + key, false);
				}
				String item = ownedTeleport(access.items, owned);
				if (!listed(lines, item))
				{
					slots += addLine(lines, ids, owned, item, 1, "to reach a fairy ring (" + access.text + "), then "
						+ code + " to " + key, false);
				}
				return slots;
			}
		}
		return -1;
	}

	/**
	 * Karambwans to combo eat when the boss hits hard, then the best food for the rest.
	 */
	private int addFood(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned, int remaining,
		boolean comboEat, String preferred, List<String> notes)
	{
		PrepData.Supplies s = data.supplies();
		int slots = 0;
		String combo = null;
		int comboTaken = 0;
		int comboIndex = -1;
		if (comboEat && s.comboFood != null)
		{
			for (String name : s.comboFood)
			{
				int have = owned.count(name, true);
				if (have > 0 && remaining > 0)
				{
					combo = name;
					comboTaken = Math.min(Math.min(COMBO_FOOD, have), remaining);
					comboIndex = lines.size();
					lines.add(new PrepPlan.InventoryLine(name, comboTaken, have,
						"eat in the same tick as your main food (combo eating)", false, owned.anyId(name, true), false));
					owned.collectBankIds(name, true, ids);
					remaining -= comboTaken;
					slots += comboTaken;
					break;
				}
			}
		}
		boolean anyFood = combo != null;
		for (PrepData.Food food : foodOrder(preferred, owned))
		{
			if (remaining <= 0)
			{
				break;
			}
			int have = owned.count(food.name, true);
			int available = food.name.equals(combo) ? have - comboTaken : have;
			if (available <= 0)
			{
				continue;
			}
			anyFood = true;
			int take = Math.min(remaining, available);
			if (food.name.equals(combo))
			{
				// the combo food is also the best food left: keep it on one line
				PrepPlan.InventoryLine line = lines.get(comboIndex);
				lines.set(comboIndex, new PrepPlan.InventoryLine(line.getName(), line.getQuantity() + take, have,
					line.getNote(), false, line.getItemId(), false));
			}
			else
			{
				lines.add(new PrepPlan.InventoryLine(food.name, take, have, "heals " + food.heal, false,
					owned.anyId(food.name, true), false));
				owned.collectBankIds(food.name, true, ids);
			}
			remaining -= take;
			slots += take;
		}
		if (!anyFood)
		{
			notes.add("No food found in your bank.");
		}
		return slots;
	}

	/**
	 * Food in order of preference, with the picked food (if owned) moved to the front.
	 */
	private List<PrepData.Food> foodOrder(String preferred, OwnedItems owned)
	{
		List<PrepData.Food> order = new ArrayList<>(data.supplies().food);
		if (preferred != null && owned.count(preferred, true) > 0)
		{
			for (int i = 0; i < order.size(); i++)
			{
				if (order.get(i).name.equals(preferred))
				{
					order.add(0, order.remove(i));
					break;
				}
			}
		}
		return order;
	}

	private List<PrepPlan.FoodOption> foodOptions(OwnedItems owned)
	{
		List<PrepPlan.FoodOption> out = new ArrayList<>();
		for (PrepData.Food food : data.supplies().food)
		{
			int have = owned.count(food.name, true);
			if (have > 0)
			{
				out.add(new PrepPlan.FoodOption(food.name, food.heal, have, owned.anyId(food.name, true)));
			}
		}
		return out;
	}

	private static List<String> locationKeys(PrepData.BossInfo info)
	{
		return info == null || info.locations == null ? Collections.emptyList() : info.locations;
	}

	/**
	 * Locations whose own needs apply for sure: the boss's only location.
	 */
	private List<String> neededLocations(PrepData.BossInfo info)
	{
		List<String> keys = locationKeys(info);
		List<String> out = new ArrayList<>();
		if (keys.size() == 1 && data.locationNeeds().containsKey(keys.get(0)))
		{
			out.add(keys.get(0));
		}
		return out;
	}

	private List<PrepPlan.TeleportOption> teleports(List<String> keys, OwnedItems owned, Request request)
	{
		List<PrepPlan.TeleportOption> out = new ArrayList<>();
		for (String key : keys)
		{
			List<PrepData.Teleport> list = data.locations().get(key);
			if (list == null)
			{
				continue;
			}
			for (PrepData.Teleport t : list)
			{
				out.add(new PrepPlan.TeleportOption(key, t.text, usable(t, request, owned)));
			}
		}
		return out;
	}

	/**
	 * The first of these teleport items the player owns charged.
	 */
	private String ownedTeleport(List<String> names, OwnedItems owned)
	{
		if (names == null)
		{
			return null;
		}
		List<String> uncharged = data.supplies().unchargedTeleports;
		for (String n : names)
		{
			int have = owned.count(n, false);
			if (uncharged != null && uncharged.contains(n))
			{
				have -= owned.count(n, true);
			}
			// an inert Xeric's talisman can't teleport until it's recharged
			have -= owned.count(n + " (inert)", true);
			if (have > 0)
			{
				return n;
			}
		}
		return null;
	}

	private static boolean listed(List<PrepPlan.InventoryLine> lines, String name)
	{
		return lines.stream().anyMatch(l -> l.getName().equals(name));
	}

	private static boolean matchesAny(String name, List<String> bases)
	{
		if (bases == null)
		{
			return false;
		}
		for (String base : bases)
		{
			if (OwnedItems.matches(name, base))
			{
				return true;
			}
		}
		return false;
	}

	private static int addLine(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned, String name,
		int quantity, String note, boolean required)
	{
		int have = owned.count(name, false);
		int take = Math.max(1, Math.min(quantity, have));
		lines.add(new PrepPlan.InventoryLine(name, take, have, note, required, owned.anyId(name, false), false));
		collect(name, false, owned, ids);
		return take;
	}

	/**
	 * A stackable item (runes, darts): the whole stack in one slot.
	 */
	private static int addStack(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned, String name,
		String note)
	{
		int have = owned.count(name, false);
		lines.add(new PrepPlan.InventoryLine(name, Math.max(1, have), have, note, false, owned.anyId(name, false), true));
		collect(name, false, owned, ids);
		return 1;
	}

	private static void collect(String name, boolean exact, OwnedItems owned, Set<Integer> ids)
	{
		if (name != null)
		{
			owned.collectBankIds(name, exact, ids);
		}
	}

	private static String firstOwnedExact(List<String> names, OwnedItems owned)
	{
		for (String n : names)
		{
			if (owned.count(n, true) > 0)
			{
				return n;
			}
		}
		return null;
	}

	static String firstOwned(List<String> names, OwnedItems owned)
	{
		if (names == null)
		{
			return null;
		}
		for (String n : names)
		{
			if (owned.owns(n))
			{
				return n;
			}
		}
		return null;
	}

	private static boolean ownsAny(List<String> names, OwnedItems owned, Collection<String> ownedGearNames)
	{
		for (String n : names)
		{
			if (ownedGearNames.contains(n) || owned.owns(n))
			{
				return true;
			}
		}
		return false;
	}
}
