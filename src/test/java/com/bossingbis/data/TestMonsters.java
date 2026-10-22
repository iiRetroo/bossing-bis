package com.bossingbis.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Non-boss monsters the DPS tests check against (the wiki calculator cases were worked out on
 * them), kept in test-monsters.json so the plugin's own data holds only bosses.
 */
public final class TestMonsters
{
	private static List<Monster> monsters;

	private TestMonsters()
	{
	}

	/**
	 * A test monster by its exact name, preferring the Slayer-flagged, standard version (Abyssal
	 * demon finds the standard one, not the Catacombs one).
	 */
	public static Monster named(String name)
	{
		Monster best = null;
		for (Monster m : all())
		{
			if (m.getName().equals(name) && (best == null || rank(m) > rank(best)))
			{
				best = m;
			}
		}
		if (best == null)
		{
			throw new IllegalArgumentException("no test monster named " + name);
		}
		return best;
	}

	public static Monster byId(int id)
	{
		for (Monster m : all())
		{
			if (m.getId() == id)
			{
				return m;
			}
		}
		throw new IllegalArgumentException("no test monster " + id);
	}

	private static int rank(Monster m)
	{
		return (m.isSlayerMonster() ? 2 : 0)
			+ (m.getVersion().isEmpty() || m.getVersion().equalsIgnoreCase("Standard") ? 1 : 0);
	}

	private static synchronized List<Monster> all()
	{
		if (monsters != null)
		{
			return monsters;
		}
		List<Monster> out = new ArrayList<>();
		try (Reader reader = new InputStreamReader(
			TestMonsters.class.getResourceAsStream("/com/bossingbis/test-monsters.json"), StandardCharsets.UTF_8))
		{
			for (JsonElement e : new Gson().fromJson(reader, JsonArray.class))
			{
				// same columns as monsters.json
				JsonArray a = e.getAsJsonArray();
				Set<String> attrs = new HashSet<>();
				for (JsonElement attr : a.get(16).getAsJsonArray())
				{
					attrs.add(attr.getAsString());
				}
				out.add(new Monster(
					a.get(0).getAsInt(), a.get(1).getAsString(), a.get(2).getAsString(), a.get(3).getAsInt(),
					a.get(4).getAsInt(), a.get(5).getAsInt(), a.get(6).getAsInt(), a.get(7).getAsInt(),
					a.get(8).getAsInt(), a.get(9).getAsInt(), a.get(10).getAsInt(), a.get(11).getAsInt(),
					a.get(12).getAsInt(), a.get(13).getAsInt(), a.get(14).getAsInt(), a.get(15).getAsInt(),
					Collections.unmodifiableSet(attrs), a.get(17).getAsInt() == 1,
					a.get(18).getAsString(), a.get(19).getAsInt()));
			}
		}
		catch (Exception ex)
		{
			throw new IllegalStateException("can't read test-monsters.json", ex);
		}
		monsters = out;
		return out;
	}
}
