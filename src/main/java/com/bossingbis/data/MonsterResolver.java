package com.bossingbis.data;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds monsters in the bundled data: the monsters a boss entry names, or a monster by name.
 */
public class MonsterResolver
{
	/**
	 * Group names that do not match a single monster name.
	 */
	private static final Map<String, String> GROUP_NAMES = ImmutableMap.<String, String>builder()
		.put("kalphites", "Kalphite Soldier")
		.put("metal dragons", "Steel dragon")
		.put("aviansies", "Aviansie")
		.build();

	private static final int MAX_CANDIDATES = 40;

	private final GameData data;

	public MonsterResolver(GameData data)
	{
		this.data = data;
	}

	/**
	 * The monsters a boss entry names, in order. An entry is either "Name (version)" for one
	 * version, or "Name" for every version of that monster.
	 */
	public List<Monster> forBoss(List<String> entries)
	{
		List<Monster> out = new ArrayList<>();
		if (entries == null)
		{
			return out;
		}
		for (String entry : entries)
		{
			String lower = entry.trim().toLowerCase(Locale.ROOT);
			boolean found = false;
			for (Monster m : data.getMonsters())
			{
				if (m.displayName().toLowerCase(Locale.ROOT).equals(lower) && !out.contains(m))
				{
					out.add(m);
					found = true;
				}
			}
			if (found)
			{
				continue;
			}
			for (Monster m : data.getMonsters())
			{
				if (out.size() < MAX_CANDIDATES && m.getName().toLowerCase(Locale.ROOT).equals(lower) && !out.contains(m))
				{
					out.add(m);
				}
			}
		}
		return out;
	}

	/**
	 * A monster by name, singular or plural ("Abyssal demons" finds Abyssal demon), preferring
	 * the standard version.
	 */
	public Monster fromName(String name)
	{
		if (name == null || name.trim().isEmpty())
		{
			return null;
		}
		String lower = name.trim().toLowerCase(Locale.ROOT);
		String group = GROUP_NAMES.get(lower);
		if (group != null)
		{
			Monster m = exactMatch(group.toLowerCase(Locale.ROOT));
			if (m != null)
			{
				return m;
			}
		}

		List<String> candidates = new ArrayList<>(singulars(lower));
		for (String c : candidates)
		{
			Monster m = exactMatch(c);
			if (m != null)
			{
				return m;
			}
		}
		for (String c : candidates)
		{
			Monster m = prefixMatch(c);
			if (m != null)
			{
				return m;
			}
		}
		return null;
	}

	private static Set<String> singulars(String name)
	{
		String t = name.startsWith("the ") ? name.substring(4) : name;
		Set<String> out = new LinkedHashSet<>();
		out.add(t);
		if (t.endsWith("ies"))
		{
			out.add(t.substring(0, t.length() - 3) + "y");
		}
		if (t.endsWith("ves"))
		{
			out.add(t.substring(0, t.length() - 3) + "f");
		}
		if (t.endsWith("es"))
		{
			out.add(t.substring(0, t.length() - 2));
		}
		if (t.endsWith("s"))
		{
			out.add(t.substring(0, t.length() - 1));
		}
		return out;
	}

	private Monster exactMatch(String lowerName)
	{
		Monster best = null;
		for (Monster m : data.getMonsters())
		{
			if (m.getName().toLowerCase(Locale.ROOT).equals(lowerName) && better(m, best))
			{
				best = m;
			}
		}
		return best;
	}

	private Monster prefixMatch(String lowerName)
	{
		Monster best = null;
		for (Monster m : data.getMonsters())
		{
			if (m.getName().toLowerCase(Locale.ROOT).startsWith(lowerName) && better(m, best))
			{
				best = m;
			}
		}
		return best;
	}

	/**
	 * Prefer slayer-flagged, standard versions of a monster over quest, minigame or boss variants.
	 */
	private static boolean better(Monster m, Monster current)
	{
		if (current == null)
		{
			return true;
		}
		return rank(m) > rank(current);
	}

	private static int rank(Monster m)
	{
		int r = 0;
		if (m.isSlayerMonster())
		{
			r += 2;
		}
		if (m.getVersion().isEmpty() || m.getVersion().equalsIgnoreCase("Standard"))
		{
			r += 1;
		}
		return r;
	}
}
