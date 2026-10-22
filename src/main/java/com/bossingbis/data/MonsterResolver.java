package com.bossingbis.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Finds the monsters a boss entry names in the bundled data.
 */
public class MonsterResolver
{
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
}
