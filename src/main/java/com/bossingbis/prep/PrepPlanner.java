package com.bossingbis.prep;

import com.bossingbis.data.AttackType;
import com.bossingbis.data.EquipmentSlot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
	 * Whether the boss's monsters bunch up, so bursting or barraging is worth working out.
	 */
	public boolean stacks(String boss)
	{
		PrepData.BossInfo info = data.boss(boss);
		return info != null && info.stacks;
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
		int prayerPotions;
		@Builder.Default
		PrayerRestore prayerRestore = PrayerRestore.POTIONS_FIRST;
		int combatPotions;
		boolean fillFood;
		/** The monster being fought, for its drops. */
		String monsterName;
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
		for (PrepData.InventoryItem item : carried)
		{
			String name = item.exact ? firstOwnedExact(item.names, owned) : firstOwned(item.names, owned);
			if ((name == null && item.optional) || (name != null && request.getWornNames().contains(name)))
			{
				continue;
			}
			int have = name != null ? owned.count(name, item.exact) : 0;
			lines.add(new PrepPlan.InventoryLine(name != null ? name : item.names.get(0), 1, have, item.note,
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
				slots += addLine(lines, ids, owned, anti, 1, "this boss can poison or envenom you", false);
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
		boolean magic = bestType == AttackType.MAGIC || bestType == AttackType.BURST;
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
		else if (bestType != null && bestType.isMelee() && combatPotions > 0
			&& (firstOwned(s.attackPotions, owned) != null || firstOwned(s.strengthPotions, owned) != null))
		{
			// no combat potion: separate attack and strength potions boost the same stats
			String attack = firstOwned(s.attackPotions, owned);
			String strength = firstOwned(s.strengthPotions, owned);
			if (attack != null)
			{
				slots += addLine(lines, ids, owned, attack, combatPotions, "boosts your Attack", false);
			}
			if (strength != null)
			{
				slots += addLine(lines, ids, owned, strength, combatPotions, "boosts your Strength", false);
			}
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

		if (request.isSuggestSpecWeapon() && request.getMonsterHitpoints() >= s.specMinHitpoints && s.specWeapons != null)
		{
			for (PrepData.SpecWeapon spec : s.specWeapons)
			{
				if (owned.owns(spec.name) && !request.getPlannedWeapons().contains(spec.name) && !listed(lines, spec.name))
				{
					slots += addLine(lines, ids, owned, spec.name, 1, "special attack: " + spec.note, false);
					break;
				}
			}
		}

		if (request.getLootMaxKills() > 0)
		{
			slots += addLootTools(lines, ids, owned, monsterName, request.getLootMaxKills());
		}

		List<String> tips = new ArrayList<>(locationTips);
		if (info != null && info.tips != null)
		{
			tips.addAll(info.tips);
		}
		if (info != null && info.stacks)
		{
			String pouch = firstOwned(s.lootTools == null ? null : s.lootTools.get("alchables"), owned);
			if (pouch != null && !listed(lines, pouch))
			{
				slots += addLine(lines, ids, owned, pouch, 1, "with burst or barrage runes", false);
			}
			String lure = bestType == AttackType.BURST ? firstOwned(s.lureWeapons, owned) : null;
			if (lure != null)
			{
				slots += addStack(lines, ids, owned, lure, "throw one at each monster to gather a stack, then burst it");
			}
		}

		slots += addTeleports(lines, ids, owned, locationKeys, tips);

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

		return new PrepPlan(request.getRequirements(), lines, teleports(locationKeys, owned), notes, tips, slots,
			Collections.unmodifiableSet(ids), foodOptions(owned));
	}

	/**
	 * Items that collect or make use of what the monster drops, for the ones the player owns.
	 */
	private int addLootTools(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned,
		String monsterName, int lootMaxKills)
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
			String tool = firstOwned(s.lootTools.get(type[0]), owned);
			if (tool != null)
			{
				if (listed(lines, tool))
				{
					continue;
				}
				String note = monsterName + " drops " + type[1] + " (" + often + ")"
					+ ("alchables".equals(type[0]) ? "; fill it with nature and fire runes for High Alchemy" : "");
				slots += addLine(lines, ids, owned, tool, 1, note, false);
			}
			else if ("alchables".equals(type[0]))
			{
				// no rune pouch: nature runes still take just one slot
				String runes = firstOwned(s.alchRunes, owned);
				if (runes != null)
				{
					slots += addStack(lines, ids, owned, runes, monsterName + " drops " + type[1] + " (" + often
						+ "); bring a fire staff for High Alchemy");
				}
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
			notes.add(mode == PrayerRestore.POTIONS_ONLY
				? "No prayer potions or super restores found."
				: "No prayer potions, super restores or moonlight moth mixes found.");
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
	 * One owned teleport item to the boss (the first location that has one), so the plan doesn't
	 * carry several ways there. Worn teleports become a tip instead.
	 */
	private int addTeleports(List<PrepPlan.InventoryLine> lines, Set<Integer> ids, OwnedItems owned,
		List<String> keys, List<String> tips)
	{
		PrepData.Supplies s = data.supplies();
		for (String key : keys)
		{
			List<PrepData.Teleport> list = data.locations().get(key);
			if (list == null)
			{
				continue;
			}
			for (PrepData.Teleport t : list)
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
		}
		return 0;
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

	private List<PrepPlan.TeleportOption> teleports(List<String> keys, OwnedItems owned)
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
				Boolean has = t.items == null || t.items.isEmpty() ? null : ownedTeleport(t.items, owned) != null;
				out.add(new PrepPlan.TeleportOption(key, t.text, has));
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
