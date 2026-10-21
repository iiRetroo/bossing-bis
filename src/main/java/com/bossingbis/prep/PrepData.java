package com.bossingbis.prep;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Boss preparation data from bosses.json and supplies.json. Both files are hand-maintained;
 * see the comment at the top of each for the format.
 */
public final class PrepData
{
	public static final class BossInfo
	{
		/** Shown in the boss picker. */
		String name;
		/** Monsters to work out gear against, "Name (version)" or "Name"; the first is the default. */
		List<String> monsters;
		/** Every listed monster is fought on each trip (phases, or several monsters together). */
		boolean wholeFight;
		/** The Slayer task this boss can only be fought on, or null if it needs none. */
		String slayerTask;
		Map<String, List<String>> required;
		List<Preference> prefer;
		List<InventoryItem> inventory;
		String dragonfire;
		boolean venom;
		/** Monsters bunch up, so multi-target spells work well. */
		boolean stacks;
		/** The boss hits hard enough that combo eating helps. */
		boolean comboEat;
		/** The least the worn gear must weigh, in kg; 0 for no limit. */
		double minWeight;
		List<String> tips;
		List<String> locations;

		public String getName()
		{
			return name;
		}

		public List<String> getMonsters()
		{
			return monsters == null ? Collections.emptyList() : monsters;
		}

		public boolean isWholeFight()
		{
			return wholeFight;
		}

		public String getSlayerTask()
		{
			return slayerTask;
		}
	}

	/**
	 * A worn item the wiki recommends for this boss for reasons the DPS maths can't see; kept when
	 * the best set without it is no more than withinPercent better.
	 */
	static final class Preference
	{
		String slot;
		List<String> items;
		int withinPercent;
		String reason;
	}

	static final class InventoryItem
	{
		List<String> names;
		String note;
		/** Fits in one inventory slot however many there are. */
		boolean stackable;
		/** Useful but not needed, so not flagged when missing. */
		boolean optional;
		/** Match names exactly ("Fungicide" must not match "Fungicide spray"). */
		boolean exact;
		/**
		 * A swap the wiki recommends for part of the fight, carried only when owned and when the
		 * Wiki switches setting is on. The note says what it's for, never when to use it.
		 */
		@SerializedName("switch")
		boolean switchItem;
		/** Only for this version of the boss (matched in its version, e.g. "Awakened"), or null for all. */
		String onlyFor;
	}

	/** What a location needs whatever the boss. */
	static final class LocationNeeds
	{
		Map<String, List<String>> required;
		List<InventoryItem> inventory;
		List<String> tips;
		/** Shown as a tip when the boss may or may not be reached through here. */
		String summary;
	}

	static final class SpecWeapon
	{
		String name;
		String note;
	}

	static final class Teleport
	{
		String text;
		List<String> items;
	}

	/**
	 * A teleport spell: its spellbook, Magic level, runes per cast and the quest that unlocks it.
	 */
	static final class TeleportSpell
	{
		String spellbook;
		int level;
		Map<String, Integer> runes;
		/** A RuneLite Quest name, or null when it needs none. */
		String quest;
	}

	static final class Food
	{
		String name;
		int heal;
	}

	static final class Supplies
	{
		List<Food> food;
		/** Super combat potions, best first. */
		List<String> meleePotions;
		/** Super attack and strength potions, used together when there's no super combat potion. */
		List<String> attackPotions;
		List<String> strengthPotions;
		/** Weaker fallbacks: a combat potion, then attack and strength potions. */
		List<String> weakMeleePotions;
		List<String> weakAttackPotions;
		List<String> weakStrengthPotions;
		List<String> rangedPotions;
		List<String> magicPotions;
		List<String> magicBoostItems;
		List<String> prayerPotions;
		/** Cheaper prayer restores; two of these stand in for one prayer potion. */
		List<String> prayerMixes;
		/** Food eaten in the same tick as other food (combo eating). */
		List<String> comboFood;
		/** Teleport items worn rather than carried, so they need no inventory slot. */
		List<String> wornTeleports;
		/** Teleport items that stack in one slot. */
		List<String> stackableTeleports;
		/** Exact names of the uncharged versions of teleport jewellery, which can't teleport. */
		List<String> unchargedTeleports;
		List<String> superAntifire;
		List<String> antifire;
		List<String> antivenom;
		/** Of the antivenom list, the ones that cure poison but don't protect against venom. */
		List<String> poisonOnly;
		List<String> dragonfireShields;
		List<String> wyvernShields;
		/** Loot type (herbs, seeds, gems, bones, ashes, alchables) to the items that collect it. */
		Map<String, List<String>> lootTools;
		/** Cheap throwing weapons to tag monsters and gather them into a stack before bursting, cheapest first. */
		List<String> lureWeapons;
		/** Defence-reducing special attack weapons, best first. */
		List<SpecWeapon> specWeapons;
		/** A spec weapon is suggested for monsters with at least this much HP. */
		int specMinHitpoints;
		/** Teleport spells by name, as they start a route's text. */
		Map<String, TeleportSpell> teleportSpells;
		/** Staffs that let you use fairy rings. */
		List<String> fairyRingStaffs;
		/** Ways to reach a fairy ring, best first. */
		List<Teleport> fairyRingAccess;
		/** Rune pouches, best first, with how many rune types each holds. */
		Map<String, Integer> runePouches;
		/** Runes per High Alchemy cast. */
		Map<String, Integer> alchemyRunes;
		/** Worn items that provide unlimited runes, with the runes each gives. */
		Map<String, List<String>> runeStaves;
		/** Combination runes, with the runes each counts as. */
		Map<String, List<String>> comboRunes;
	}

	private static final class BossesFile
	{
		LinkedHashMap<String, BossInfo> bosses;
		Map<String, List<Teleport>> locations;
		Map<String, LocationNeeds> locationNeeds;
	}

	private final Map<String, BossInfo> bosses;
	private final Map<String, List<Teleport>> locations;
	private final Map<String, LocationNeeds> locationNeeds;
	private final Supplies supplies;
	/** Lower-case monster name to loot type to average kills per item of that type. */
	private final Map<String, Map<String, Integer>> drops;

	private PrepData(Map<String, BossInfo> bosses, Map<String, List<Teleport>> locations,
		Map<String, LocationNeeds> locationNeeds, Supplies supplies, Map<String, Map<String, Integer>> drops)
	{
		this.bosses = bosses;
		this.locations = locations;
		this.locationNeeds = locationNeeds;
		this.supplies = supplies;
		this.drops = drops;
	}

	public static PrepData load(Gson gson) throws IOException
	{
		BossesFile bossesFile;
		try (Reader r = open("bosses.json"))
		{
			bossesFile = gson.fromJson(r, BossesFile.class);
		}
		Supplies supplies;
		try (Reader r = open("supplies.json"))
		{
			supplies = gson.fromJson(r, Supplies.class);
		}
		Map<String, Map<String, Integer>> drops = new HashMap<>();
		try (Reader r = open("drops.json"))
		{
			JsonObject root = gson.fromJson(r, JsonObject.class);
			for (Map.Entry<String, JsonElement> monster : root.entrySet())
			{
				Map<String, Integer> kinds = new HashMap<>();
				for (Map.Entry<String, JsonElement> kind : monster.getValue().getAsJsonObject().entrySet())
				{
					kinds.put(kind.getKey(), kind.getValue().getAsInt());
				}
				drops.put(monster.getKey().toLowerCase(Locale.ROOT), kinds);
			}
		}
		return new PrepData(
			bossesFile.bosses == null ? Collections.emptyMap() : Collections.unmodifiableMap(bossesFile.bosses),
			bossesFile.locations == null ? Collections.emptyMap() : bossesFile.locations,
			bossesFile.locationNeeds == null ? Collections.emptyMap() : bossesFile.locationNeeds,
			supplies, drops);
	}

	private static Reader open(String name) throws IOException
	{
		InputStream in = PrepData.class.getResourceAsStream("/com/bossingbis/" + name);
		if (in == null)
		{
			throw new IOException("Missing resource " + name);
		}
		return new InputStreamReader(in, StandardCharsets.UTF_8);
	}

	public BossInfo boss(String key)
	{
		return key == null ? null : bosses.get(key.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * Every boss by key, in the order of the data file.
	 */
	public Map<String, BossInfo> bosses()
	{
		return bosses;
	}

	Map<String, List<Teleport>> locations()
	{
		return locations;
	}

	Map<String, LocationNeeds> locationNeeds()
	{
		return locationNeeds;
	}

	Supplies supplies()
	{
		return supplies;
	}

	/**
	 * The quests that unlock the teleport spells, as RuneLite Quest names, for the plugin to check.
	 */
	public java.util.Set<String> spellQuests()
	{
		java.util.Set<String> out = new java.util.HashSet<>();
		if (supplies.teleportSpells != null)
		{
			for (TeleportSpell spell : supplies.teleportSpells.values())
			{
				if (spell.quest != null)
				{
					out.add(spell.quest);
				}
			}
		}
		return out;
	}

	/**
	 * Loot type to average kills per item of that type, for a monster; empty if unknown.
	 */
	Map<String, Integer> drops(String monsterName)
	{
		Map<String, Integer> d = monsterName == null ? null : drops.get(monsterName.toLowerCase(Locale.ROOT));
		return d == null ? Collections.emptyMap() : d;
	}
}
