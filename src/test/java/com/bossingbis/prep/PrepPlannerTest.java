package com.bossingbis.prep;

import com.bossingbis.data.AttackType;
import com.bossingbis.data.EquipmentSlot;
import com.bossingbis.data.GameData;
import com.bossingbis.data.MonsterResolver;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Offline checks of the boss preparation data and suggestions. No game client involved.
 */
public class PrepPlannerTest
{
	private static PrepData data;
	private static PrepPlanner planner;
	private static GameData gameData;

	@BeforeClass
	public static void load() throws Exception
	{
		data = PrepData.load(new Gson());
		planner = new PrepPlanner(data);
		gameData = GameData.load(new Gson());
	}

	private static OwnedItems owned(Object... nameQty)
	{
		Map<String, Integer> q = new HashMap<>();
		Map<String, Set<Integer>> ids = new HashMap<>();
		for (int i = 0; i < nameQty.length; i += 2)
		{
			q.put((String) nameQty[i], (Integer) nameQty[i + 1]);
			ids.put((String) nameQty[i], new HashSet<>(Collections.singletonList(1000 + i)));
		}
		return OwnedItems.of(q, ids);
	}

	private static PrepPlanner.Request.RequestBuilder request(String boss, AttackType best, OwnedItems owned)
	{
		return PrepPlanner.Request.builder().boss(boss).bestType(best).owned(owned).prayerPotions(4)
			.combatPotions(1).fillFood(true);
	}

	private static List<String> names(PrepPlan plan)
	{
		List<String> out = new ArrayList<>();
		for (PrepPlan.InventoryLine l : plan.getInventory())
		{
			out.add(l.getName());
		}
		return out;
	}

	@Test
	public void namesMatchDosesAndCharges()
	{
		assertTrue(OwnedItems.matches("Prayer potion(3)", "Prayer potion"));
		assertTrue(OwnedItems.matches("Games necklace(8)", "Games necklace"));
		assertFalse(OwnedItems.matches("Magic potion(4)", "Divine magic potion"));
		assertFalse(OwnedItems.matches("Anti-venom+(4)", "Anti-venom"));
	}

	@Test
	public void everyBossHasMonstersLocationsAndTeleports()
	{
		MonsterResolver resolver = new MonsterResolver(gameData);
		assertTrue(data.bosses().size() >= 40);
		for (Map.Entry<String, PrepData.BossInfo> e : data.bosses().entrySet())
		{
			String key = e.getKey();
			PrepData.BossInfo info = e.getValue();
			assertEquals(key + " key must be lower case", key.toLowerCase(Locale.ROOT), key);
			assertNotNull(key + " has no name", info.name);
			assertFalse(key + " has no monsters", info.getMonsters().isEmpty());
			for (String m : info.getMonsters())
			{
				assertFalse(key + ": no monster stats for " + m, resolver.forBoss(List.of(m)).isEmpty());
			}
			assertNotNull(key + " has no locations", info.locations);
			for (String location : info.locations)
			{
				List<PrepData.Teleport> teleports = data.locations().get(location);
				assertNotNull(key + " lists unknown location " + location, teleports);
				assertFalse(location + " has no teleports", teleports.isEmpty());
				for (PrepData.Teleport t : teleports)
				{
					assertFalse(location + " has a teleport without text", t.text == null || t.text.trim().isEmpty());
				}
			}
		}
	}

	@Test
	public void slayerOnlyBossesAreMarked()
	{
		for (String boss : new String[]{"cerberus", "abyssal sire", "kraken", "thermonuclear smoke devil",
			"alchemical hydra", "grotesque guardians", "araxxor", "shellbane gryphon"})
		{
			assertNotNull(boss + " should need a Slayer task", data.boss(boss).getSlayerTask());
		}
		for (String boss : new String[]{"vorkath", "zulrah", "general graardor", "corporeal beast", "nex"})
		{
			assertEquals(boss + " needs no Slayer task", null, data.boss(boss).getSlayerTask());
		}
	}

	@Test
	public void requiredAndPreferredItemNamesExistInTheItemData()
	{
		for (Map.Entry<String, PrepData.BossInfo> e : data.bosses().entrySet())
		{
			PrepData.BossInfo info = e.getValue();
			if (info.required != null)
			{
				for (List<String> names : info.required.values())
				{
					for (String n : names)
					{
						if (!n.equals("Boots of brimstone"))
						{
							assertNotNull(e.getKey() + ": unknown item " + n, gameData.findItem(n));
						}
					}
				}
			}
			if (info.prefer != null)
			{
				for (PrepData.Preference p : info.prefer)
				{
					assertNotNull(e.getKey() + ": unknown slot " + p.slot, EquipmentSlot.fromKey(p.slot));
					assertFalse(e.getKey() + ": a preference needs a reason", p.reason == null || p.reason.isEmpty());
					for (String n : p.items)
					{
						assertNotNull(e.getKey() + ": unknown item " + n, gameData.findItem(n));
					}
				}
			}
		}
	}

	/**
	 * The data must stay a planner for before the trip. Gear reasons may mention mechanics and prayer
	 * effects, as the wiki does, but never tell you what to do in the fight: no prayer instructions,
	 * timing, positioning or what's coming next.
	 */
	@Test
	public void bossDataHasNoFightHelp() throws Exception
	{
		String text;
		try (java.io.InputStream in = PrepData.class.getResourceAsStream("/com/bossingbis/bosses.json"))
		{
			text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
		}
		// the format note at the top names what's forbidden; check everything after it
		text = text.substring(text.indexOf("\"bosses\""));
		for (String banned : new String[]{
			// positioning
			"safespot", "safe spot", "stand ", "standing", "tile", "dodge",
			// prayer instructions
			"protect from", "pray against", "switch prayer", "turn on", "activate", "piety", "rigour", "augury",
			// timing and what's coming
			"flinch", "next attack", "when it", "when he", "when she", "when the", "as soon as", "swap to"})
		{
			assertFalse("boss data mentions \"" + banned + "\"", text.contains(banned));
		}
	}

	@Test
	public void entryItemsTeleportAndFood()
	{
		OwnedItems bank = owned("Mith grapple", 1, "Prayer potion(4)", 10, "Ranging potion(4)", 2, "Shark", 100);
		PrepPlan plan = planner.plan(request("kree'arra", AttackType.RANGED, bank).build());
		assertEquals("Mith grapple", plan.getInventory().get(0).getName());
		assertTrue(names(plan).contains("Ranging potion(4)") || names(plan).contains("Ranging potion"));
		assertEquals(PrepPlanner.INVENTORY_SLOTS, plan.getSlotsUsed());
		assertTrue(plan.getTeleports().stream().allMatch(t -> t.getLocation().equals("God Wars Dungeon")));
	}

	@Test
	public void crossbowForTheGrappleUnlessOneIsWorn()
	{
		OwnedItems bank = owned("Mith grapple", 1, "Rune crossbow", 1, "Armadyl crossbow", 1, "Shark", 100);
		PrepPlan carried = planner.plan(request("kree'arra", AttackType.RANGED, bank).build());
		assertTrue(names(carried).contains("Rune crossbow"));
		// wearing any crossbow in the planned set: no extra one
		PrepPlan worn = planner.plan(request("kree'arra", AttackType.RANGED, bank)
			.wornNames(Set.of("Armadyl crossbow")).build());
		assertTrue(names(worn).stream().noneMatch(n -> n.endsWith("crossbow")));
		assertTrue(names(worn).contains("Mith grapple"));
	}

	@Test
	public void godWarsBossesGetTheFollowersTip()
	{
		for (String boss : new String[]{"general graardor", "commander zilyana", "k'ril tsutsaroth", "kree'arra", "nex"})
		{
			assertTrue(boss, data.boss(boss).tips.stream().anyMatch(t -> t.contains("followers")));
		}
		assertFalse(planner.preferences("vorkath").isEmpty());
		assertEquals(EquipmentSlot.RING, planner.preferences("zulrah").get(0).getSlot());
	}

	@Test
	public void batchTwoReviewData()
	{
		// a dragonfire-protecting shield for the KBD, a recoil ring for the Kalphite Queen, Aranea boots for Sarachnis
		assertEquals(EquipmentSlot.SHIELD, planner.preferences("king black dragon").get(0).getSlot());
		assertEquals(EquipmentSlot.RING, planner.preferences("kalphite queen").get(0).getSlot());
		assertEquals(EquipmentSlot.FEET, planner.preferences("sarachnis").get(0).getSlot());
		assertTrue(planner.poisons("dagannoth kings"));
		assertTrue(planner.poisons("kalphite queen"));
		// optional entry items only show when owned
		PrepPlan without = planner.plan(request("dagannoth kings", AttackType.RANGED, owned("Shark", 50)).build());
		assertFalse(names(without).contains("Rune thrownaxe"));
		PrepPlan with = planner.plan(request("dagannoth kings", AttackType.RANGED,
			owned("Rune thrownaxe", 1, "Pet rock", 1, "Shark", 50)).build());
		assertTrue(names(with).containsAll(List.of("Rune thrownaxe", "Pet rock")));
	}

	@Test
	public void mechanicReasonsFromTheWiki()
	{
		// Spectral spirit shield for its prayer-drain reduction
		assertEquals("Spectral spirit shield", planner.preferences("k'ril tsutsaroth").get(0).getItems().get(0));
		assertEquals("Spectral spirit shield", planner.preferences("nex").get(0).getItems().get(0));
		// Ghommal's hilt for the Barrows tunnels, best tier owned
		PrepPlan barrows = planner.plan(request("barrows brothers", AttackType.MAGIC,
			owned("Spade", 1, "Ghommal's hilt 2", 1, "Ghommal's hilt 4", 1, "Shark", 50)).build());
		assertTrue(names(barrows).contains("Ghommal's hilt 4"));
		assertFalse(names(barrows).contains("Ghommal's hilt 2"));
		// Corp: the stat-draining special attack weapons, alongside the usual spec weapon
		PrepPlan corp = planner.plan(request("corporeal beast", AttackType.STAB,
			owned("Arclight", 1, "Bandos godsword", 1, "Dragon warhammer", 1, "Shark", 50))
			.monsterHitpoints(2000).suggestSpecWeapon(true).build());
		assertTrue(names(corp).containsAll(List.of("Arclight", "Bandos godsword", "Dragon warhammer")));
		// more ways to Slepe
		assertTrue(planner.plan(request("the nightmare", AttackType.SLASH, owned("Ectophial", 1)).build())
			.getTeleports().stream().anyMatch(t -> t.getText().startsWith("Ectophial") && Boolean.TRUE.equals(t.getOwned())));
	}

	@Test
	public void wikiSwitchesAreOwnedOnlyAndCanBeTurnedOff()
	{
		OwnedItems bank = owned("Hallowfell", 1, "Blisterwood stake", 1, "Shark", 50);
		PrepPlan on = planner.plan(request("phosani's nightmare", AttackType.CRUSH, bank).build());
		assertTrue(names(on).containsAll(List.of("Hallowfell", "Blisterwood stake")));
		// Dinh's bulwark isn't owned, so it isn't suggested
		assertFalse(names(on).contains("Dinh's bulwark"));
		assertTrue(on.getInventory().stream().filter(l -> l.getName().equals("Hallowfell"))
			.allMatch(l -> l.getNote().startsWith("wiki switch: ")));
		PrepPlan off = planner.plan(request("phosani's nightmare", AttackType.CRUSH, bank).wikiSwitches(false).build());
		assertFalse(names(off).contains("Hallowfell"));
		// an item already worn in the set isn't carried as a switch too
		PrepPlan worn = planner.plan(request("phosani's nightmare", AttackType.CRUSH, bank)
			.wornNames(Set.of("Hallowfell")).build());
		assertFalse(names(worn).contains("Hallowfell"));
	}

	@Test
	public void awakenersOrbOnlyForAwakenedFights()
	{
		OwnedItems bank = owned("Awakener's orb", 2, "Pestle and mortar", 1, "Shark", 50);
		PrepPlan normal = planner.plan(request("duke sucellus", AttackType.CRUSH, bank)
			.monsterVersion("Post-quest, Awake").build());
		assertFalse(names(normal).contains("Awakener's orb"));
		assertTrue(names(normal).contains("Pestle and mortar"));
		PrepPlan awakened = planner.plan(request("duke sucellus", AttackType.CRUSH, bank)
			.monsterVersion("Awakened, Awake").build());
		assertTrue(names(awakened).contains("Awakener's orb"));
		// the Whisperer can't be started without a blackstone fragment
		PrepPlan whisperer = planner.plan(request("the whisperer", AttackType.MAGIC, owned()).build());
		assertTrue(whisperer.getNotes().stream().anyMatch(n -> n.contains("Blackstone fragment")));
	}

	@Test
	public void inertTalismanIsNotATeleport()
	{
		PrepPlan inert = planner.plan(request("sarachnis", AttackType.SLASH, owned("Xeric's talisman (inert)", 1)).build());
		assertTrue(inert.getTeleports().stream().filter(t -> t.getText().startsWith("Xeric's talisman"))
			.allMatch(t -> Boolean.FALSE.equals(t.getOwned())));
		PrepPlan charged = planner.plan(request("sarachnis", AttackType.SLASH, owned("Xeric's talisman", 1)).build());
		assertTrue(names(charged).contains("Xeric's talisman"));
	}

	@Test
	public void missingEntryItemIsFlagged()
	{
		PrepPlan plan = planner.plan(request("kree'arra", AttackType.RANGED, owned()).build());
		assertTrue(plan.getNotes().stream().anyMatch(n -> n.contains("Mith grapple")));
	}

	@Test
	public void requiredGearForBosses()
	{
		List<PrepPlan.Requirement> hydra = planner.requirements("alchemical hydra", owned(), Collections.emptySet());
		assertEquals(EquipmentSlot.FEET, hydra.get(0).getSlot());
		List<PrepPlan.Requirement> shellbane = planner.requirements("shellbane gryphon", owned(), Collections.emptySet());
		assertTrue(shellbane.stream().anyMatch(r -> r.getAccepted().contains("Tortugan shield")));
		assertEquals(40000, planner.minWeight("shellbane gryphon"));
		// Vorkath: a dragonfire shield unless you have a super antifire
		assertEquals(1, planner.requirements("vorkath", owned(), Collections.emptySet()).size());
		assertEquals(0, planner.requirements("vorkath", owned("Extended super antifire(4)", 2),
			Collections.emptySet()).size());
	}

	@Test
	public void antifireAntivenomAndKarambwans()
	{
		OwnedItems bank = owned("Extended super antifire(4)", 3, "Anti-venom+(4)", 2, "Cooked karambwan", 20,
			"Shark", 100);
		PrepPlan plan = planner.plan(request("vorkath", AttackType.RANGED, bank).build());
		List<String> names = names(plan);
		assertTrue(names.contains("Extended super antifire(4)") || names.contains("Extended super antifire"));
		assertTrue(names.stream().anyMatch(n -> n.startsWith("Anti-venom")));
		assertTrue(names.contains("Cooked karambwan"));
		// a charged Serpentine helm in the set makes anti-venom unnecessary
		PrepPlan immune = planner.plan(request("vorkath", AttackType.RANGED, bank).venomImmune(true).build());
		assertTrue(names(immune).stream().noneMatch(n -> n.startsWith("Anti-venom")));
	}

	@Test
	public void switchesAreCarriedBeforeSupplies()
	{
		OwnedItems bank = owned("Toxic blowpipe", 1, "Trident of the swamp", 1, "Prayer potion(4)", 10, "Shark", 100);
		PrepPlan plan = planner.plan(request("zulrah", AttackType.MAGIC, bank)
			.switches(List.of(new PrepPlan.Switch("Toxic blowpipe", "switch for Zulrah (Magma)"))).build());
		assertEquals("Toxic blowpipe", plan.getInventory().get(0).getName());
		assertEquals(PrepPlanner.INVENTORY_SLOTS, plan.getSlotsUsed());
	}

	@Test
	public void specWeaponSkipsPlannedWeapons()
	{
		// a plain hammer for the door, so the Dragon warhammer isn't the entry item
		OwnedItems bank = owned("Hammer", 1, "Dragon warhammer", 1, "Bandos godsword", 1, "Shark", 100);
		PrepPlan plan = planner.plan(request("general graardor", AttackType.CRUSH, bank).monsterHitpoints(255)
			.suggestSpecWeapon(true).plannedWeapons(Set.of("Dragon warhammer")).build());
		assertTrue(names(plan).contains("Bandos godsword"));
		assertFalse(names(plan).contains("Dragon warhammer"));
	}

	@Test
	public void wornTeleportsAreATipNotASlot()
	{
		PrepPlan plan = planner.plan(request("general graardor", AttackType.CRUSH,
			owned("Ghommal's hilt 4", 1, "Hammer", 1)).build());
		assertTrue(plan.getTips().stream().anyMatch(t -> t.contains("Ghommal's hilt")));
	}

	@Test
	public void karuulmBootsApplyToHydra()
	{
		PrepPlan plan = planner.plan(request("alchemical hydra", AttackType.RANGED, owned("Shark", 10)).build());
		assertFalse(plan.getTips().stream().anyMatch(t -> t.startsWith("If you go to")));
	}

	@Test
	public void pickedFoodComesFirst()
	{
		OwnedItems bank = owned("Shark", 100, "Anglerfish", 100);
		PrepPlan plan = planner.plan(request("giant mole", AttackType.SLASH, bank).prayerPotions(0)
			.preferredFood("Shark").build());
		assertTrue(names(plan).contains("Shark"));
		assertFalse(names(plan).contains("Anglerfish"));
		assertEquals(2, plan.getFoodOptions().size());
	}
}
